import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { startTestServer } from "./test-server.js";

function createSessionId() {
  return "8d6d3a2c-8d6a-4bf2-a0cf-f77a45ef27ab";
}

function createIdempotencyKey() {
  return `idem-project-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

test("POST /projects crea proyecto y lo asocia a sesion", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const sessionId = createSessionId();
    const response = await fetch(`${server.baseUrl}/projects`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": sessionId,
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({
        title: "Proyecto desde test",
        description: "Descripcion opcional"
      })
    });

    assert.equal(response.status, 201);
    const body = await response.json();
    assert.ok(body.projectId);

    const row = server.db
      .prepare("SELECT id, session_id, title, description FROM projects WHERE id = ?")
      .get(body.projectId);
    assert.ok(row);
    assert.equal(row.id, body.projectId);
    assert.equal(row.session_id, sessionId);
    assert.equal(row.title, "Proyecto desde test");
    assert.equal(row.description, "Descripcion opcional");
  } finally {
    await server.close();
  }
});

test("POST /projects retorna 400 cuando title es invalido", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const response = await fetch(`${server.baseUrl}/projects`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": createSessionId(),
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({
        title: "    "
      })
    });

    assert.equal(response.status, 400);
    const body = await response.json();
    assert.equal(body.error.code, "INVALID_TITLE");
  } finally {
    await server.close();
  }
});

test("POST /projects retorna 401 cuando falta X-Session-Id", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const response = await fetch(`${server.baseUrl}/projects`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({
        title: "Proyecto sin sesion"
      })
    });

    assert.equal(response.status, 401);
    const body = await response.json();
    assert.equal(body.error.code, "SESSION_REQUIRED");
  } finally {
    await server.close();
  }
});

test("POST /projects retorna 400 cuando falta X-Idempotency-Key", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const response = await fetch(`${server.baseUrl}/projects`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": createSessionId()
      },
      body: JSON.stringify({
        title: "Proyecto sin idempotency key"
      })
    });

    assert.equal(response.status, 400);
    const body = await response.json();
    assert.equal(body.error.code, "IDEMPOTENCY_KEY_REQUIRED");
  } finally {
    await server.close();
  }
});

test("POST /projects reusa respuesta con misma key y mismo payload", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);
  const sessionId = createSessionId();
  const idempotencyKey = createIdempotencyKey();
  const payload = JSON.stringify({
    title: "Proyecto idempotente",
    description: "descripcion estable"
  });

  try {
    const firstResponse = await fetch(`${server.baseUrl}/projects`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": sessionId,
        "x-idempotency-key": idempotencyKey
      },
      body: payload
    });
    const firstBody = await firstResponse.json();
    assert.equal(firstResponse.status, 201);
    assert.ok(firstBody.projectId);

    const secondResponse = await fetch(`${server.baseUrl}/projects`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": sessionId,
        "x-idempotency-key": idempotencyKey
      },
      body: payload
    });
    const secondBody = await secondResponse.json();

    assert.equal(secondResponse.status, 201);
    assert.deepEqual(secondBody, firstBody);

    const rows = server.db
      .prepare("SELECT id FROM projects WHERE session_id = ? ORDER BY created_at ASC")
      .all(sessionId);
    assert.equal(rows.length, 1);
    assert.equal(rows[0].id, firstBody.projectId);
  } finally {
    await server.close();
  }
});

test("POST /projects retorna 409 con misma key y payload distinto", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);
  const sessionId = createSessionId();
  const idempotencyKey = createIdempotencyKey();

  try {
    const firstResponse = await fetch(`${server.baseUrl}/projects`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": sessionId,
        "x-idempotency-key": idempotencyKey
      },
      body: JSON.stringify({ title: "Payload A" })
    });
    assert.equal(firstResponse.status, 201);

    const conflictResponse = await fetch(`${server.baseUrl}/projects`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": sessionId,
        "x-idempotency-key": idempotencyKey
      },
      body: JSON.stringify({ title: "Payload B" })
    });
    const conflictBody = await conflictResponse.json();

    assert.equal(conflictResponse.status, 409);
    assert.equal(conflictBody.error.code, "IDEMPOTENCY_KEY_CONFLICT");
  } finally {
    await server.close();
  }
});
