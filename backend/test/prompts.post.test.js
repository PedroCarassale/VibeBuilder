import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { createApp } from "../src/app.js";
import { createDatabase } from "../src/db.js";

const SESSION_ID = "8d6d3a2c-8d6a-4bf2-a0cf-f77a45ef27ab";

async function startTestServer(dbPath, options = {}) {
  const db = createDatabase(dbPath);
  const app = createApp({
    db,
    generationProvider: options.generationProvider,
    generationTimeoutMs: options.generationTimeoutMs
  });

  await new Promise((resolve) => app.listen(0, resolve));
  const address = app.address();
  const baseUrl = `http://127.0.0.1:${address.port}`;

  return {
    db,
    baseUrl,
    close: () => new Promise((resolve) => app.close(resolve))
  };
}

async function createProject(baseUrl) {
  const response = await fetch(`${baseUrl}/projects`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-session-id": SESSION_ID
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
        "x-session-id": SESSION_ID
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
    assert.deepEqual(body.providerMeta, {
      provider: "mock-v0",
      model: "v0-simulated",
      requestId: "req-success-1",
      finishReason: "completed",
      latencyMs: 12
    });

    const promptRow = server.db
      .prepare("SELECT id, project_id, version_id, role, content FROM prompt_messages WHERE id = ?")
      .get(body.promptMessageId);
    assert.ok(promptRow);
    assert.equal(promptRow.id, body.promptMessageId);
    assert.equal(promptRow.project_id, projectId);
    assert.equal(promptRow.version_id, body.projectVersionId);
    assert.equal(promptRow.role, "user");
    assert.equal(promptRow.content, "Crea una landing para una academia de baile");

    const versionRow = readVersionRow(server.db, body.projectVersionId);
    assert.ok(versionRow);
    assert.equal(versionRow.id, body.projectVersionId);
    assert.equal(versionRow.project_id, projectId);
    assert.equal(versionRow.version_number, 1);
    assert.equal(versionRow.prompt_snapshot, "Crea una landing para una academia de baile");
    assert.equal(versionRow.status, "success");

    const persistedProviderMeta = JSON.parse(versionRow.provider_meta);
    assert.deepEqual(persistedProviderMeta, body.providerMeta);
    assert.equal("secretToken" in persistedProviderMeta, false);

    const projectRow = server.db
      .prepare("SELECT current_version_id FROM projects WHERE id = ?")
      .get(projectId);
    assert.equal(projectRow.current_version_id, body.projectVersionId);

    const { versionCount, messageCount } = countProjectRows(server.db, projectId);
    assert.equal(versionCount, 1);
    assert.equal(messageCount, 1);
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
          "x-session-id": SESSION_ID
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
        "x-session-id": SESSION_ID
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

test("POST /projects/:projectId/prompts marca failed por timeout del proveedor", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const provider = {
    name: "mock-v0",
    async generate({ signal }) {
      await new Promise((resolve, reject) => {
        const timeoutId = setTimeout(resolve, 100);
        signal.addEventListener(
          "abort",
          () => {
            clearTimeout(timeoutId);
            const error = new Error("aborted");
            error.name = "AbortError";
            reject(error);
          },
          { once: true }
        );
      });
      return { providerMeta: { model: "v0-simulated" } };
    }
  };
  const server = await startTestServer(dbPath, {
    generationProvider: provider,
    generationTimeoutMs: 10
  });

  try {
    const { projectId } = await createProject(server.baseUrl);

    const response = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID
      },
      body: JSON.stringify({
        prompt: "Genera un dashboard de ventas"
      })
    });

    assert.equal(response.status, 201);
    const body = await response.json();
    assert.equal(body.status, "failed");
    assert.deepEqual(body.providerMeta, {
      provider: "mock-v0",
      errorType: "timeout",
      errorCode: "PROVIDER_TIMEOUT",
      retryable: true
    });

    const versionRow = readVersionRow(server.db, body.projectVersionId);
    assert.equal(versionRow.status, "failed");
    assert.deepEqual(JSON.parse(versionRow.provider_meta), body.providerMeta);
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
    const requestOptions = {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID
      },
      body: payload
    };

    const failedResponse = await fetch(
      `${server.baseUrl}/projects/${projectId}/prompts`,
      requestOptions
    );
    assert.equal(failedResponse.status, 201);
    const failedBody = await failedResponse.json();
    assert.equal(failedBody.versionNumber, 1);
    assert.equal(failedBody.status, "failed");

    const retryResponse = await fetch(
      `${server.baseUrl}/projects/${projectId}/prompts`,
      requestOptions
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
    assert.equal(messageCount, 2);
  } finally {
    await server.close();
  }
});
