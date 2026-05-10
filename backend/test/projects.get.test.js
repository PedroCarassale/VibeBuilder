import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { createApp } from "../src/app.js";
import { createDatabase } from "../src/db.js";

const SESSION_A = "8d6d3a2c-8d6a-4bf2-a0cf-f77a45ef27ab";
const SESSION_B = "c4ee8d66-0ec8-47fe-8e84-b4ed803f7253";

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

async function createProject(baseUrl, sessionId, title, description = null) {
  const response = await fetch(`${baseUrl}/projects`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-session-id": sessionId
    },
    body: JSON.stringify({ title, description })
  });

  assert.equal(response.status, 201);
  return response.json();
}

test("GET /projects devuelve proyectos de la sesion actual", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const { projectId } = await createProject(server.baseUrl, SESSION_A, "Proyecto A");

    const response = await fetch(`${server.baseUrl}/projects`, {
      headers: {
        "x-session-id": SESSION_A
      }
    });

    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.length, 1);
    assert.equal(body[0].id, projectId);
    assert.equal(body[0].title, "Proyecto A");
    assert.equal(body[0].description, null);
    assert.ok(body[0].createdAt);
    assert.ok(body[0].updatedAt);
  } finally {
    await server.close();
  }
});

test("GET /projects no mezcla proyectos de otras sesiones", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    await createProject(server.baseUrl, SESSION_A, "Proyecto sesion A");
    await createProject(server.baseUrl, SESSION_B, "Proyecto sesion B");

    const response = await fetch(`${server.baseUrl}/projects`, {
      headers: {
        "x-session-id": SESSION_A
      }
    });

    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.length, 1);
    assert.equal(body[0].title, "Proyecto sesion A");
  } finally {
    await server.close();
  }
});

test("GET /projects ordena por updatedAt descendente", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const insertProject = server.db.prepare(`
      INSERT INTO projects (
        id,
        session_id,
        title,
        description,
        current_version_id,
        created_at,
        updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?);
    `);

    insertProject.run(
      "project-old",
      SESSION_A,
      "Proyecto antiguo",
      null,
      null,
      "2026-01-01T10:00:00.000Z",
      "2026-01-01T10:00:00.000Z"
    );
    insertProject.run(
      "project-new",
      SESSION_A,
      "Proyecto reciente",
      null,
      null,
      "2026-01-01T11:00:00.000Z",
      "2026-01-01T11:00:00.000Z"
    );

    const response = await fetch(`${server.baseUrl}/projects`, {
      headers: {
        "x-session-id": SESSION_A
      }
    });

    assert.equal(response.status, 200);
    const body = await response.json();
    assert.deepEqual(
      body.map((project) => project.id),
      ["project-new", "project-old"]
    );
  } finally {
    await server.close();
  }
});

test("GET /projects responde [] con 200 cuando no hay proyectos", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const response = await fetch(`${server.baseUrl}/projects`, {
      headers: {
        "x-session-id": SESSION_A
      }
    });

    assert.equal(response.status, 200);
    const body = await response.json();
    assert.deepEqual(body, []);
  } finally {
    await server.close();
  }
});
