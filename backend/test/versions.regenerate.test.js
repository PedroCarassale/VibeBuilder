import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { startTestServer } from "./test-server.js";

const SESSION_ID = "8d6d3a2c-8d6a-4bf2-a0cf-f77a45ef27ab";
const OTHER_SESSION_ID = "1c98e8cf-5647-4bf5-9e26-c52255c5b857";
let idempotencySequence = 0;

function createIdempotencyKey() {
  idempotencySequence += 1;
  return `idem-regenerate-${idempotencySequence}`;
}

async function createProject(baseUrl, sessionId = SESSION_ID) {
  const response = await fetch(`${baseUrl}/projects`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-session-id": sessionId,
      "x-idempotency-key": createIdempotencyKey()
    },
    body: JSON.stringify({ title: "Proyecto regenerable" })
  });

  assert.equal(response.status, 201);
  return response.json();
}

async function sendPrompt(baseUrl, projectId, prompt, sessionId = SESSION_ID) {
  const response = await fetch(`${baseUrl}/projects/${projectId}/prompts`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-session-id": sessionId,
      "x-idempotency-key": createIdempotencyKey()
    },
    body: JSON.stringify({ prompt })
  });
  assert.equal(response.status, 201);
  return response.json();
}

async function regenerate(baseUrl, projectId, versionId, body = {}, idempotencyKey = createIdempotencyKey(), sessionId = SESSION_ID) {
  const response = await fetch(`${baseUrl}/projects/${projectId}/versions/${versionId}/regenerate`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-session-id": sessionId,
      "x-idempotency-key": idempotencyKey
    },
    body: JSON.stringify(body)
  });
  return {
    status: response.status,
    body: await response.json()
  };
}

function createSequenceProvider(steps) {
  let index = 0;
  return {
    name: "mock-v0",
    async generate() {
      const step = steps[index] ?? steps.at(-1);
      index += 1;
      if (step === "fail") {
        throw new Error("provider down");
      }
      return {
        providerMeta: {
          model: "v0-simulated",
          requestId: `req-${index}`
        },
        assistantText: `Versión ${index} lista.`
      };
    }
  };
}

test("POST /projects/:projectId/versions/:versionId/regenerate crea nueva version sin sobrescribir la fallida", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-regenerate-"));
  const server = await startTestServer(path.join(tmpDir, "db.sqlite"), {
    generationProvider: createSequenceProvider(["fail", "success"])
  });

  try {
    const { projectId } = await createProject(server.baseUrl);
    const failed = await sendPrompt(server.baseUrl, projectId, "Prompt original");
    assert.equal(failed.status, "failed");

    const response = await regenerate(server.baseUrl, projectId, failed.projectVersionId);
    assert.equal(response.status, 201);
    assert.equal(response.body.status, "success");
    assert.equal(response.body.versionNumber, 2);
    assert.equal(response.body.sourceVersionId, failed.projectVersionId);
    assert.equal(response.body.attemptNumber, 2);
    assert.equal(response.body.failureCode, null);

    const rows = server.db
      .prepare(
        `SELECT id, version_number, status, prompt_snapshot, source_version_id, attempt_number
         FROM project_versions
         WHERE project_id = ?
         ORDER BY version_number ASC;`
      )
      .all(projectId);
    assert.deepEqual(
      rows.map((row) => ({
        id: row.id,
        version_number: row.version_number,
        status: row.status,
        prompt_snapshot: row.prompt_snapshot,
        source_version_id: row.source_version_id,
        attempt_number: row.attempt_number
      })),
      [
        {
          id: failed.projectVersionId,
          version_number: 1,
          status: "failed",
          prompt_snapshot: "Prompt original",
          source_version_id: null,
          attempt_number: 1
        },
        {
          id: response.body.projectVersionId,
          version_number: 2,
          status: "success",
          prompt_snapshot: "Prompt original",
          source_version_id: failed.projectVersionId,
          attempt_number: 2
        }
      ]
    );

    const project = server.db
      .prepare("SELECT current_version_id FROM projects WHERE id = ?")
      .get(projectId);
    assert.equal(project.current_version_id, response.body.projectVersionId);
  } finally {
    await server.close();
  }
});

test("POST regenerate es idempotente y detecta payload distinto", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-regenerate-"));
  let calls = 0;
  const server = await startTestServer(path.join(tmpDir, "db.sqlite"), {
    generationProvider: {
      name: "mock-v0",
      async generate() {
        calls += 1;
        if (calls === 1) throw new Error("first fails");
        return { providerMeta: { model: "v0-simulated", requestId: "req-idem" } };
      }
    }
  });

  try {
    const { projectId } = await createProject(server.baseUrl);
    const failed = await sendPrompt(server.baseUrl, projectId, "Prompt idempotente");
    const key = createIdempotencyKey();

    const first = await regenerate(server.baseUrl, projectId, failed.projectVersionId, {}, key);
    const second = await regenerate(server.baseUrl, projectId, failed.projectVersionId, {}, key);
    const conflict = await regenerate(
      server.baseUrl,
      projectId,
      failed.projectVersionId,
      { prompt: "Prompt corregido" },
      key
    );

    assert.equal(first.status, 201);
    assert.equal(second.status, 201);
    assert.deepEqual(second.body, first.body);
    assert.equal(conflict.status, 409);
    assert.equal(conflict.body.error.code, "IDEMPOTENCY_KEY_CONFLICT");
    assert.equal(calls, 2);

    const versionCount = server.db
      .prepare("SELECT COUNT(*) AS total FROM project_versions WHERE project_id = ?")
      .get(projectId).total;
    assert.equal(versionCount, 2);
  } finally {
    await server.close();
  }
});

test("POST regenerate devuelve 409 para version success y 404 para otra sesion", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-regenerate-"));
  const server = await startTestServer(path.join(tmpDir, "db.sqlite"), {
    generationProvider: createSequenceProvider(["success"])
  });

  try {
    const { projectId } = await createProject(server.baseUrl);
    const success = await sendPrompt(server.baseUrl, projectId, "Prompt exitoso");

    const nonRegenerable = await regenerate(server.baseUrl, projectId, success.projectVersionId);
    assert.equal(nonRegenerable.status, 409);
    assert.equal(nonRegenerable.body.error.code, "VERSION_NOT_REGENERABLE");

    const otherSession = await regenerate(
      server.baseUrl,
      projectId,
      success.projectVersionId,
      {},
      createIdempotencyKey(),
      OTHER_SESSION_ID
    );
    assert.equal(otherSession.status, 404);
  } finally {
    await server.close();
  }
});

test("POST regenerate fallida preserva current_version_id y guarda prompt corregido", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-regenerate-"));
  const server = await startTestServer(path.join(tmpDir, "db.sqlite"), {
    generationProvider: createSequenceProvider(["success", "fail", "fail"])
  });

  try {
    const { projectId } = await createProject(server.baseUrl);
    const stable = await sendPrompt(server.baseUrl, projectId, "Version estable");
    const failed = await sendPrompt(server.baseUrl, projectId, "Prompt roto");

    const response = await regenerate(server.baseUrl, projectId, failed.projectVersionId, {
      prompt: "Prompt corregido pero proveedor falla"
    });
    assert.equal(response.status, 201);
    assert.equal(response.body.status, "failed");
    assert.equal(response.body.failureCode, "PROVIDER_ERROR");

    const project = server.db
      .prepare("SELECT current_version_id FROM projects WHERE id = ?")
      .get(projectId);
    assert.equal(project.current_version_id, stable.projectVersionId);

    const attempts = server.db
      .prepare(
        `SELECT version_number, status, prompt_snapshot, source_version_id, attempt_number, failure_code
         FROM project_versions
         WHERE project_id = ?
         ORDER BY version_number ASC;`
      )
      .all(projectId);
    assert.deepEqual(
      attempts.map((row) => ({
        version_number: row.version_number,
        status: row.status,
        prompt_snapshot: row.prompt_snapshot,
        source_version_id: row.source_version_id,
        attempt_number: row.attempt_number,
        failure_code: row.failure_code
      })),
      [
        {
          version_number: 1,
          status: "success",
          prompt_snapshot: "Version estable",
          source_version_id: null,
          attempt_number: 1,
          failure_code: null
        },
        {
          version_number: 2,
          status: "failed",
          prompt_snapshot: "Prompt roto",
          source_version_id: null,
          attempt_number: 1,
          failure_code: "PROVIDER_ERROR"
        },
        {
          version_number: 3,
          status: "failed",
          prompt_snapshot: "Prompt corregido pero proveedor falla",
          source_version_id: failed.projectVersionId,
          attempt_number: 2,
          failure_code: "PROVIDER_ERROR"
        }
      ]
    );
  } finally {
    await server.close();
  }
});

