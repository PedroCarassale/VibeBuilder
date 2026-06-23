import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { startTestServer } from "./test-server.js";

const SESSION_A = "8d6d3a2c-8d6a-4bf2-a0cf-f77a45ef27ab";
const SESSION_B = "c4ee8d66-0ec8-47fe-8e84-b4ed803f7253";
let sequence = 0;

async function createProject(baseUrl, title = "Original", description = "Descripción") {
  const response = await fetch(`${baseUrl}/projects`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-session-id": SESSION_A,
      "x-idempotency-key": `management-${++sequence}`
    },
    body: JSON.stringify({ title, description })
  });
  assert.equal(response.status, 201);
  return (await response.json()).projectId;
}

async function patchProject(baseUrl, projectId, body, sessionId = SESSION_A) {
  return fetch(`${baseUrl}/projects/${projectId}`, {
    method: "PATCH",
    headers: { "content-type": "application/json", "x-session-id": sessionId },
    body: typeof body === "string" ? body : JSON.stringify(body)
  });
}

test("PATCH actualiza campos parciales, permite limpiar descripción y persiste", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-management-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  let server = await startTestServer(dbPath);
  const projectId = await createProject(server.baseUrl);
  const originalUpdatedAt = "2020-01-01T00:00:00.000Z";
  server.db.prepare("UPDATE projects SET updated_at = ? WHERE id = ?").run(originalUpdatedAt, projectId);

  let response = await patchProject(server.baseUrl, projectId, { title: "  Renombrado  " });
  assert.equal(response.status, 200);
  let body = await response.json();
  assert.equal(body.title, "Renombrado");
  assert.equal(body.description, "Descripción");
  assert.ok(body.updatedAt > originalUpdatedAt);

  response = await patchProject(server.baseUrl, projectId, { description: "   " });
  assert.equal(response.status, 200);
  body = await response.json();
  assert.equal(body.title, "Renombrado");
  assert.equal(body.description, null);

  await server.close();
  server = await startTestServer(dbPath);
  try {
    const list = await fetch(`${server.baseUrl}/projects`, { headers: { "x-session-id": SESSION_A } });
    assert.equal((await list.json())[0].title, "Renombrado");
  } finally {
    await server.close();
  }
});

test("POST aplica límites de título y descripción", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-management-"));
  const server = await startTestServer(path.join(tmpDir, "db.sqlite"));
  try {
    for (const payload of [
      { title: "x".repeat(101) },
      { title: "Válido", description: "x".repeat(501) }
    ]) {
      const response = await fetch(`${server.baseUrl}/projects`, {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "x-session-id": SESSION_A,
          "x-idempotency-key": `management-${++sequence}`
        },
        body: JSON.stringify(payload)
      });
      assert.equal(response.status, 400);
    }
    assert.equal(server.db.prepare("SELECT COUNT(*) total FROM projects").get().total, 0);
  } finally {
    await server.close();
  }
});

test("PATCH rechaza cuerpos inválidos sin mutar", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-management-"));
  const server = await startTestServer(path.join(tmpDir, "db.sqlite"));
  try {
    const projectId = await createProject(server.baseUrl);
    const before = server.db.prepare("SELECT title, description, updated_at FROM projects WHERE id = ?").get(projectId);
    const invalidBodies = [
      "{bad",
      {},
      { unknown: true },
      { title: "   " },
      { title: "x".repeat(101) },
      { description: "x".repeat(501) }
    ];
    for (const invalid of invalidBodies) {
      const response = await patchProject(server.baseUrl, projectId, invalid);
      assert.equal(response.status, 400);
    }
    const after = server.db.prepare("SELECT title, description, updated_at FROM projects WHERE id = ?").get(projectId);
    assert.deepEqual(after, before);
  } finally {
    await server.close();
  }
});

test("PATCH y DELETE ocultan proyectos ajenos como 404", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-management-"));
  const server = await startTestServer(path.join(tmpDir, "db.sqlite"));
  try {
    const projectId = await createProject(server.baseUrl);
    const patchResponse = await patchProject(server.baseUrl, projectId, { title: "Intruso" }, SESSION_B);
    const deleteResponse = await fetch(`${server.baseUrl}/projects/${projectId}`, {
      method: "DELETE",
      headers: { "x-session-id": SESSION_B }
    });
    assert.equal(patchResponse.status, 404);
    assert.equal(deleteResponse.status, 404);
  } finally {
    await server.close();
  }
});

test("DELETE hace soft delete, conserva filas y bloquea todos los endpoints", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-management-"));
  const server = await startTestServer(path.join(tmpDir, "db.sqlite"));
  try {
    const projectId = await createProject(server.baseUrl);
    server.db.prepare(`INSERT INTO project_versions
      (id, project_id, version_number, prompt_snapshot, status, preview_url, provider_meta, created_at)
      VALUES (?, ?, 1, 'prompt', 'success', 'https://example.com', '{}', ?)`)
      .run("version-kept", projectId, new Date().toISOString());
    server.db.prepare(`INSERT INTO prompt_messages
      (id, project_id, version_id, role, content, created_at) VALUES (?, ?, ?, 'user', 'prompt', ?)`)
      .run("message-kept", projectId, "version-kept", new Date().toISOString());
    server.db.prepare("UPDATE projects SET current_version_id = ? WHERE id = ?").run("version-kept", projectId);

    const deleted = await fetch(`${server.baseUrl}/projects/${projectId}`, {
      method: "DELETE",
      headers: { "x-session-id": SESSION_A }
    });
    assert.equal(deleted.status, 204);
    assert.equal(await deleted.text(), "");

    const list = await fetch(`${server.baseUrl}/projects`, { headers: { "x-session-id": SESSION_A } });
    assert.deepEqual(await list.json(), []);
    for (const suffix of ["messages", "versions", "preview"]) {
      const response = await fetch(`${server.baseUrl}/projects/${projectId}/${suffix}`, {
        headers: { "x-session-id": SESSION_A }
      });
      assert.equal(response.status, 404);
      assert.equal((await response.json()).error.code, "PROJECT_NOT_FOUND");
    }
    const prompt = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_A,
        "x-idempotency-key": `management-${++sequence}`
      },
      body: JSON.stringify({ prompt: "Cambio" })
    });
    assert.equal(prompt.status, 404);
    assert.equal(server.db.prepare("SELECT COUNT(*) total FROM project_versions WHERE project_id = ?").get(projectId).total, 1);
    assert.equal(server.db.prepare("SELECT COUNT(*) total FROM prompt_messages WHERE project_id = ?").get(projectId).total, 1);

    const repeated = await fetch(`${server.baseUrl}/projects/${projectId}`, {
      method: "DELETE",
      headers: { "x-session-id": SESSION_A }
    });
    assert.equal(repeated.status, 404);
  } finally {
    await server.close();
  }
});
