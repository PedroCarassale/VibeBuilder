import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { startTestServer } from "./test-server.js";

const SESSION_ID = "8d6d3a2c-8d6a-4bf2-a0cf-f77a45ef27ab";
let idempotencySequence = 0;

function createIdempotencyKey() {
  idempotencySequence += 1;
  return `idem-prompts-${idempotencySequence}`;
}


async function createProject(baseUrl) {
  const response = await fetch(`${baseUrl}/projects`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-session-id": SESSION_ID,
      "x-idempotency-key": createIdempotencyKey()
    },
    body: JSON.stringify({ title: "Proyecto T6" })
  });

  assert.equal(response.status, 201);
  return response.json();
}

function readVersionRow(db, projectVersionId) {
  return db
    .prepare(
      "SELECT id, project_id, version_number, prompt_snapshot, status, provider_meta FROM project_versions WHERE id = ?"
    )
    .get(projectVersionId);
}

function countProjectRows(db, projectId) {
  const versionCount = db
    .prepare("SELECT COUNT(*) AS count FROM project_versions WHERE project_id = ?")
    .get(projectId).count;
  const messageCount = db
    .prepare("SELECT COUNT(*) AS count FROM prompt_messages WHERE project_id = ?")
    .get(projectId).count;

  return { versionCount, messageCount };
}

test("POST /projects/:projectId/prompts crea PromptMessage y ProjectVersion en success", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const provider = {
    name: "mock-v0",
    async generate() {
      return {
        providerMeta: {
          model: "v0-simulated",
          requestId: "req-success-1",
          finishReason: "completed",
          latencyMs: 12,
          secretToken: "should-not-persist"
        },
        assistantText: "Respuesta de prueba del mock."
      };
    }
  };
  const server = await startTestServer(dbPath, { generationProvider: provider });

  try {
    const { projectId } = await createProject(server.baseUrl);

    const response = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({
        prompt: "Crea una landing para una academia de baile"
      })
    });

    assert.equal(response.status, 201);
    const body = await response.json();
    assert.ok(body.promptMessageId);
    assert.ok(body.projectVersionId);
    assert.equal(body.versionNumber, 1);
    assert.equal(body.status, "success");
    assert.equal(body.providerMeta.provider, "mock-v0");
    assert.equal(body.providerMeta.model, "v0-simulated");
    assert.equal(body.providerMeta.requestId, "req-success-1");
    assert.equal(body.providerMeta.finishReason, "completed");
    assert.equal(body.providerMeta.latencyMs, 12);
    assert.match(body.providerMeta.previewUrl ?? "", /^https:\/\/preview\.v0\.dev\//);

    const promptRow = server.db
      .prepare("SELECT id, project_id, version_id, role, content FROM prompt_messages WHERE id = ?")
      .get(body.promptMessageId);
    assert.ok(promptRow);
    assert.equal(promptRow.id, body.promptMessageId);
    assert.equal(promptRow.project_id, projectId);
    assert.equal(promptRow.version_id, body.projectVersionId);
    assert.equal(promptRow.role, "user");
    assert.equal(promptRow.content, "Crea una landing para una academia de baile");

    const assistantRow = server.db
      .prepare("SELECT role, content FROM prompt_messages WHERE id != ? AND project_id = ?")
      .get(body.promptMessageId, projectId);
    assert.ok(assistantRow);
    assert.equal(assistantRow.role, "assistant");
    assert.equal(assistantRow.content, "Respuesta de prueba del mock.");

    const versionRow = readVersionRow(server.db, body.projectVersionId);
    assert.ok(versionRow);
    assert.equal(versionRow.id, body.projectVersionId);
    assert.equal(versionRow.project_id, projectId);
    assert.equal(versionRow.version_number, 1);
    assert.equal(versionRow.prompt_snapshot, "Crea una landing para una academia de baile");
    assert.equal(versionRow.status, "success");

    const persistedProviderMeta = JSON.parse(versionRow.provider_meta);
    assert.equal(persistedProviderMeta.provider, body.providerMeta.provider);
    assert.equal(persistedProviderMeta.requestId, body.providerMeta.requestId);
    assert.equal(persistedProviderMeta.previewUrl, body.providerMeta.previewUrl);
    assert.equal("secretToken" in persistedProviderMeta, false);

    const projectRow = server.db
      .prepare("SELECT current_version_id FROM projects WHERE id = ?")
      .get(projectId);
    assert.equal(projectRow.current_version_id, body.projectVersionId);

    const { versionCount, messageCount } = countProjectRows(server.db, projectId);
    assert.equal(versionCount, 1);
    assert.equal(messageCount, 2);
  } finally {
    await server.close();
  }
});

test("POST /projects/:projectId/prompts responde 404 si el proyecto no existe", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const response = await fetch(
      `${server.baseUrl}/projects/11111111-1111-4111-8111-111111111111/prompts`,
      {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "x-session-id": SESSION_ID,
          "x-idempotency-key": createIdempotencyKey()
        },
        body: JSON.stringify({
          prompt: "Prompt para proyecto inexistente"
        })
      }
    );

    assert.equal(response.status, 404);
    const body = await response.json();
    assert.equal(body.error.code, "PROJECT_NOT_FOUND");

    const promptCount = server.db.prepare("SELECT COUNT(*) AS count FROM prompt_messages;").get();
    const versionCount = server.db.prepare("SELECT COUNT(*) AS count FROM project_versions;").get();
    assert.equal(promptCount.count, 0);
    assert.equal(versionCount.count, 0);
  } finally {
    await server.close();
  }
});

test("POST /projects/:projectId/prompts marca failed si el proveedor falla", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const provider = {
    name: "mock-v0",
    async generate() {
      throw new Error("provider down");
    }
  };
  const server = await startTestServer(dbPath, { generationProvider: provider });

  try {
    const { projectId } = await createProject(server.baseUrl);

    const response = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({
        prompt: "Genera una app de gimnasio"
      })
    });

    assert.equal(response.status, 201);
    const body = await response.json();
    assert.equal(body.status, "failed");
    assert.deepEqual(body.providerMeta, {
      provider: "mock-v0",
      errorType: "provider_error",
      errorCode: "PROVIDER_ERROR",
      retryable: true
    });

    const versionRow = readVersionRow(server.db, body.projectVersionId);
    assert.ok(versionRow);
    assert.equal(versionRow.status, "failed");
    assert.deepEqual(JSON.parse(versionRow.provider_meta), body.providerMeta);

    const projectRow = server.db
      .prepare("SELECT current_version_id FROM projects WHERE id = ?")
      .get(projectId);
    assert.equal(projectRow.current_version_id, null);
  } finally {
    await server.close();
  }
});

test("POST /projects/:projectId/prompts mantiene current_version_id en ultimo success", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  let attempts = 0;
  const provider = {
    name: "mock-v0",
    async generate() {
      attempts += 1;
      if (attempts === 1) {
        return {
          providerMeta: {
            model: "v0-simulated",
            requestId: "req-success-before-fail"
          }
        };
      }
      throw new Error("provider down after first success");
    }
  };
  const server = await startTestServer(dbPath, { generationProvider: provider });

  try {
    const { projectId } = await createProject(server.baseUrl);

    const firstResponse = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({ prompt: "Genera una landing inicial" })
    });
    assert.equal(firstResponse.status, 201);
    const firstBody = await firstResponse.json();
    assert.equal(firstBody.status, "success");

    const secondResponse = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({ prompt: "Ahora agrega checkout" })
    });
    assert.equal(secondResponse.status, 201);
    const secondBody = await secondResponse.json();
    assert.equal(secondBody.status, "failed");

    const projectRow = server.db
      .prepare("SELECT current_version_id FROM projects WHERE id = ?")
      .get(projectId);
    assert.equal(projectRow.current_version_id, firstBody.projectVersionId);

    const currentVersionRow = server.db
      .prepare("SELECT id, status FROM project_versions WHERE id = ?")
      .get(projectRow.current_version_id);
    assert.equal(currentVersionRow.id, firstBody.projectVersionId);
    assert.equal(currentVersionRow.status, "success");
  } finally {
    await server.close();
  }
});

test("POST /projects/:projectId/prompts hace rollback y preserva current_version_id en error de cierre", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const provider = {
    name: "mock-v0",
    async generate() {
      return {
        providerMeta: {
          model: "v0-simulated",
          requestId: "req-success"
        }
      };
    }
  };
  const server = await startTestServer(dbPath, { generationProvider: provider });

  try {
    const { projectId } = await createProject(server.baseUrl);
    const firstResponse = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({ prompt: "Version base estable" })
    });
    assert.equal(firstResponse.status, 201);
    const firstBody = await firstResponse.json();
    assert.equal(firstBody.status, "success");

    server.db.exec(`
      CREATE TRIGGER fail_prompt_message_insert
      BEFORE INSERT ON prompt_messages
      BEGIN
        SELECT RAISE(ABORT, 'forced prompt_messages failure');
      END;
    `);

    const failingResponse = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({ prompt: "Intento que debe fallar al cerrar" })
    });
    assert.equal(failingResponse.status, 500);
    const failingBody = await failingResponse.json();
    assert.equal(failingBody.error.code, "INTERNAL_ERROR");

    const versions = server.db
      .prepare("SELECT id, status FROM project_versions WHERE project_id = ? ORDER BY version_number ASC")
      .all(projectId);
    assert.equal(versions.length, 1);
    assert.equal(versions[0].id, firstBody.projectVersionId);
    assert.equal(versions[0].status, "success");

    const projectRow = server.db
      .prepare("SELECT current_version_id FROM projects WHERE id = ?")
      .get(projectId);
    assert.equal(projectRow.current_version_id, firstBody.projectVersionId);
  } finally {
    await server.close();
  }
});

test("POST /projects/:projectId/prompts usa mensaje fallback si el proveedor no devuelve assistantText", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const provider = {
    name: "mock-v0",
    async generate() {
      return {
        providerMeta: {
          model: "v0-simulated"
        }
      };
    }
  };
  const server = await startTestServer(dbPath, { generationProvider: provider });

  try {
    const { projectId } = await createProject(server.baseUrl);

    const response = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({
        prompt: "Genera un dashboard de ventas"
      })
    });

    assert.equal(response.status, 201);
    const body = await response.json();
    assert.equal(body.status, "success");

    const rows = server.db
      .prepare(
        "SELECT role, content FROM prompt_messages WHERE project_id = ? ORDER BY created_at ASC, id ASC"
      )
      .all(projectId);
    assert.equal(rows.length, 2);
    assert.equal(rows[0].role, "user");
    assert.equal(rows[1].role, "assistant");
    assert.equal(
      rows[1].content,
      "Tu app ya está lista. Ábrela en el navegador para verla."
    );
  } finally {
    await server.close();
  }
});

test("POST /projects/:projectId/prompts reintento visible crea una sola version por envio", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  let attempts = 0;
  const provider = {
    name: "mock-v0",
    async generate() {
      attempts += 1;
      if (attempts === 1) {
        throw new Error("first attempt fails");
      }
      return {
        providerMeta: {
          model: "v0-simulated",
          requestId: "req-retry-success"
        }
      };
    }
  };
  const server = await startTestServer(dbPath, { generationProvider: provider });

  try {
    const { projectId } = await createProject(server.baseUrl);
    const payload = JSON.stringify({ prompt: "Genera un TODO app simple" });
    const failedResponse = await fetch(
      `${server.baseUrl}/projects/${projectId}/prompts`,
      {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "x-session-id": SESSION_ID,
          "x-idempotency-key": createIdempotencyKey()
        },
        body: payload
      }
    );
    assert.equal(failedResponse.status, 201);
    const failedBody = await failedResponse.json();
    assert.equal(failedBody.versionNumber, 1);
    assert.equal(failedBody.status, "failed");

    const retryResponse = await fetch(
      `${server.baseUrl}/projects/${projectId}/prompts`,
      {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "x-session-id": SESSION_ID,
          "x-idempotency-key": createIdempotencyKey()
        },
        body: payload
      }
    );
    assert.equal(retryResponse.status, 201);
    const retryBody = await retryResponse.json();
    assert.equal(retryBody.versionNumber, 2);
    assert.equal(retryBody.status, "success");

    const versions = server.db
      .prepare(
        "SELECT version_number, status FROM project_versions WHERE project_id = ? ORDER BY version_number ASC"
      )
      .all(projectId)
      .map((row) => ({ version_number: row.version_number, status: row.status }));
    assert.deepEqual(versions, [
      { version_number: 1, status: "failed" },
      { version_number: 2, status: "success" }
    ]);

    const { versionCount, messageCount } = countProjectRows(server.db, projectId);
    assert.equal(versionCount, 2);
    assert.equal(messageCount, 3);
  } finally {
    await server.close();
  }
});

test("POST /projects/:projectId/prompts de seguimiento crea N+1 y mantiene vinculo prompt-version", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const provider = {
    name: "mock-v0",
    async generate() {
      return { providerMeta: { model: "v0-simulated" } };
    }
  };
  const server = await startTestServer(dbPath, { generationProvider: provider });

  try {
    const { projectId } = await createProject(server.baseUrl);
    const prompts = [
      "Genera landing para cafeteria",
      "Agrega seccion de menu con precios"
    ];

    const firstResponse = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({ prompt: prompts[0] })
    });
    assert.equal(firstResponse.status, 201);
    const firstBody = await firstResponse.json();
    assert.equal(firstBody.versionNumber, 1);

    const secondResponse = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({ prompt: prompts[1] })
    });
    assert.equal(secondResponse.status, 201);
    const secondBody = await secondResponse.json();
    assert.equal(secondBody.versionNumber, 2);

    const versions = server.db
      .prepare(
        `
          SELECT id, version_number, prompt_snapshot
          FROM project_versions
          WHERE project_id = ?
          ORDER BY version_number ASC;
        `
      )
      .all(projectId);
    assert.equal(versions.length, 2);
    assert.deepEqual(
      versions.map((row) => ({ version_number: row.version_number, prompt_snapshot: row.prompt_snapshot })),
      [
        { version_number: 1, prompt_snapshot: prompts[0] },
        { version_number: 2, prompt_snapshot: prompts[1] }
      ]
    );

    const linkedMessages = server.db
      .prepare(
        `
          SELECT
            m.content,
            m.version_id,
            v.version_number
          FROM prompt_messages m
          JOIN project_versions v ON v.id = m.version_id
          WHERE m.project_id = ?
            AND m.role = 'user'
          ORDER BY v.version_number ASC;
        `
      )
      .all(projectId);
    assert.deepEqual(
      linkedMessages.map((row) => ({ content: row.content, version_number: row.version_number })),
      [
        { content: prompts[0], version_number: 1 },
        { content: prompts[1], version_number: 2 }
      ]
    );

    assert.throws(() => {
      server.db
        .prepare(
          `
            INSERT INTO project_versions (
              id,
              project_id,
              version_number,
              prompt_snapshot,
              status,
              preview_url,
              provider_meta,
              created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
          `
        )
        .run(
          "manual-duplicate-version",
          projectId,
          2,
          "No debe sobrescribir",
          "success",
          null,
          "{}",
          new Date().toISOString()
        );
    });
  } finally {
    await server.close();
  }
});

test("POST /projects/:projectId/prompts retorna 400 cuando falta X-Idempotency-Key", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const { projectId } = await createProject(server.baseUrl);
    const response = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID
      },
      body: JSON.stringify({ prompt: "Genera una landing" })
    });

    assert.equal(response.status, 400);
    const body = await response.json();
    assert.equal(body.error.code, "IDEMPOTENCY_KEY_REQUIRED");
  } finally {
    await server.close();
  }
});

test("POST /projects/:projectId/prompts reusa respuesta con misma key y payload", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  let generationCalls = 0;
  const provider = {
    name: "mock-v0",
    async generate() {
      generationCalls += 1;
      return {
        providerMeta: {
          model: "v0-simulated",
          requestId: "req-idempotent"
        }
      };
    }
  };
  const server = await startTestServer(dbPath, { generationProvider: provider });
  const idempotencyKey = createIdempotencyKey();

  try {
    const { projectId } = await createProject(server.baseUrl);
    const payload = JSON.stringify({ prompt: "Genera dashboard de analytics" });

    const firstResponse = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": idempotencyKey
      },
      body: payload
    });
    const firstBody = await firstResponse.json();

    const secondResponse = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": idempotencyKey
      },
      body: payload
    });
    const secondBody = await secondResponse.json();

    assert.equal(firstResponse.status, 201);
    assert.equal(secondResponse.status, 201);
    assert.deepEqual(secondBody, firstBody);
    assert.equal(generationCalls, 1);

    const { versionCount, messageCount } = countProjectRows(server.db, projectId);
    assert.equal(versionCount, 1);
    assert.equal(messageCount, 2);
  } finally {
    await server.close();
  }
});

test("POST /projects/:projectId/prompts retorna 409 con misma key y payload distinto", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const provider = {
    name: "mock-v0",
    async generate() {
      return { providerMeta: { model: "v0-simulated" } };
    }
  };
  const server = await startTestServer(dbPath, { generationProvider: provider });
  const idempotencyKey = createIdempotencyKey();

  try {
    const { projectId } = await createProject(server.baseUrl);

    const firstResponse = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": idempotencyKey
      },
      body: JSON.stringify({ prompt: "Prompt A" })
    });
    assert.equal(firstResponse.status, 201);

    const conflictResponse = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": idempotencyKey
      },
      body: JSON.stringify({ prompt: "Prompt B" })
    });
    const conflictBody = await conflictResponse.json();

    assert.equal(conflictResponse.status, 409);
    assert.equal(conflictBody.error.code, "IDEMPOTENCY_KEY_CONFLICT");
  } finally {
    await server.close();
  }
});
