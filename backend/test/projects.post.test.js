import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { createApp } from "../src/app.js";
import { createDatabase } from "../src/db.js";

function createSessionId() {
  return "8d6d3a2c-8d6a-4bf2-a0cf-f77a45ef27ab";
}

async function startTestServer(dbPath) {
  const db = createDatabase(dbPath);
  const app = createApp({ db });

  await new Promise((resolve) => app.listen(0, resolve));
  const address = app.address();
  const baseUrl = `http://127.0.0.1:${address.port}`;

  return {
    db,
    baseUrl,
    close: () => new Promise((resolve) => app.close(resolve))
  };
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
        "x-session-id": sessionId
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
        "x-session-id": createSessionId()
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
        "content-type": "application/json"
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
