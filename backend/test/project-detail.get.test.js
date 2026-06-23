import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { startTestServer } from "./test-server.js";

const SESSION_A = "8d6d3a2c-8d6a-4bf2-a0cf-f77a45ef27ab";
const SESSION_B = "c4ee8d66-0ec8-47fe-8e84-b4ed803f7253";
let idempotencySequence = 0;

function createIdempotencyKey() {
  idempotencySequence += 1;
  return `idem-project-detail-${idempotencySequence}`;
}

async function createProject(baseUrl, sessionId, title = "Proyecto detalle") {
  const response = await fetch(`${baseUrl}/projects`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-session-id": sessionId,
      "x-idempotency-key": createIdempotencyKey()
    },
    body: JSON.stringify({ title })
  });

  assert.equal(response.status, 201);
  return response.json();
}

test("GET /projects/:projectId/messages devuelve mensajes cronologicos", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const { projectId } = await createProject(server.baseUrl, SESSION_A);
    server.db
      .prepare(
        `
          INSERT INTO project_versions (id, project_id, version_number, prompt_snapshot, status, provider_meta, created_at)
          VALUES (?, ?, ?, ?, ?, ?, ?);
        `
      )
      .run("version-1", projectId, 1, "Primer prompt", "success", "{}", "2026-01-01T10:00:00.000Z");

    const insertMessage = server.db.prepare(
      `
        INSERT INTO prompt_messages (id, project_id, version_id, role, content, created_at)
        VALUES (?, ?, ?, ?, ?, ?);
      `
    );
    insertMessage.run(
      "message-2",
      projectId,
      "version-1",
      "assistant",
      "Respuesta del asistente",
      "2026-01-01T10:00:02.000Z"
    );
    insertMessage.run(
      "message-1",
      projectId,
      "version-1",
      "user",
      "Primer prompt",
      "2026-01-01T10:00:01.000Z"
    );

    const response = await fetch(`${server.baseUrl}/projects/${projectId}/messages`, {
      headers: { "x-session-id": SESSION_A }
    });

    assert.equal(response.status, 200);
    const body = await response.json();
    assert.deepEqual(body.map((message) => message.id), ["message-1", "message-2"]);
    assert.equal(body[0].versionNumber, 1);
    assert.equal(body[1].role, "assistant");
  } finally {
    await server.close();
  }
});

test("GET /projects/:projectId/versions devuelve payload final y orden consistente", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const { projectId } = await createProject(server.baseUrl, SESSION_A);
    const insertVersion = server.db.prepare(
      `
        INSERT INTO project_versions (id, project_id, version_number, prompt_snapshot, status, provider_meta, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?);
      `
    );
    insertVersion.run("version-1", projectId, 1, "Primer prompt", "success", "{}", "2026-01-01T10:00:00.000Z");
    insertVersion.run("version-2", projectId, 2, "Segundo prompt", "failed", "{}", "2026-01-01T10:01:00.000Z");

    const response = await fetch(`${server.baseUrl}/projects/${projectId}/versions`, {
      headers: { "x-session-id": SESSION_A }
    });

    assert.equal(response.status, 200);
    const body = await response.json();
    assert.deepEqual(body.map((version) => version.versionNumber), [2, 1]);
    assert.equal(body[0].status, "failed");
    assert.equal(body[0].promptSnapshot, "Segundo prompt");
    assert.equal(body[0].createdAt, "2026-01-01T10:01:00.000Z");
    assert.equal(body[0].projectId, projectId);
    assert.equal(body[0].sourceVersionId, null);
    assert.equal(body[0].attemptNumber, 1);
    assert.equal(body[0].failureCode, null);
    assert.deepEqual(Object.keys(body[0]).sort(), [
      "artifact",
      "attemptNumber",
      "completedAt",
      "createdAt",
      "failureCode",
      "id",
      "previewUrl",
      "projectId",
      "promptSnapshot",
      "sourceVersionId",
      "startedAt",
      "status",
      "versionNumber"
    ]);

    const secondResponse = await fetch(`${server.baseUrl}/projects/${projectId}/versions`, {
      headers: { "x-session-id": SESSION_A }
    });
    assert.equal(secondResponse.status, 200);
    const secondBody = await secondResponse.json();
    assert.deepEqual(secondBody, body);
  } finally {
    await server.close();
  }
});

test("GET /projects/:projectId/versions limita a ultimas 20 versiones", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const { projectId } = await createProject(server.baseUrl, SESSION_A);
    const insertVersion = server.db.prepare(
      `
        INSERT INTO project_versions (id, project_id, version_number, prompt_snapshot, status, provider_meta, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?);
      `
    );

    for (let versionNumber = 1; versionNumber <= 25; versionNumber += 1) {
      insertVersion.run(
        `version-${versionNumber}`,
        projectId,
        versionNumber,
        `Prompt ${versionNumber}`,
        versionNumber % 2 === 0 ? "success" : "failed",
        "{}",
        `2026-01-01T10:${String(versionNumber).padStart(2, "0")}:00.000Z`
      );
    }

    const response = await fetch(`${server.baseUrl}/projects/${projectId}/versions`, {
      headers: { "x-session-id": SESSION_A }
    });

    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.length, 20);
    assert.deepEqual(
      body.map((version) => version.versionNumber),
      Array.from({ length: 20 }, (_, index) => 25 - index)
    );
    assert.equal(body[0].promptSnapshot, "Prompt 25");
    assert.equal(body[19].promptSnapshot, "Prompt 6");
  } finally {
    await server.close();
  }
});

test("GET detalle responde 404 cuando el proyecto es de otra sesion", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const { projectId } = await createProject(server.baseUrl, SESSION_A);

    const versionsResponse = await fetch(`${server.baseUrl}/projects/${projectId}/versions`, {
      headers: { "x-session-id": SESSION_B }
    });
    const messagesResponse = await fetch(`${server.baseUrl}/projects/${projectId}/messages`, {
      headers: { "x-session-id": SESSION_B }
    });

    assert.equal(versionsResponse.status, 404);
    assert.equal(messagesResponse.status, 404);
    assert.equal((await versionsResponse.json()).error.code, "PROJECT_NOT_FOUND");
    assert.equal((await messagesResponse.json()).error.code, "PROJECT_NOT_FOUND");
  } finally {
    await server.close();
  }
});
