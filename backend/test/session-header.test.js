import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { createApp } from "../src/app.js";
import { createDatabase } from "../src/db.js";

const VALID_SESSION_ID = "8d6d3a2c-8d6a-4bf2-a0cf-f77a45ef27ab";
let idempotencySequence = 0;

function createIdempotencyKey() {
  idempotencySequence += 1;
  return `idem-session-${idempotencySequence}`;
}

async function startTestServer(dbPath) {
  const db = createDatabase(dbPath);
  const app = createApp({ db });

  await new Promise((resolve) => app.listen(0, resolve));
  const address = app.address();
  const baseUrl = `http://127.0.0.1:${address.port}`;

  return {
    baseUrl,
    close: () => new Promise((resolve) => app.close(resolve))
  };
}

async function createProject(baseUrl) {
  const response = await fetch(`${baseUrl}/projects`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-session-id": VALID_SESSION_ID,
      "x-idempotency-key": createIdempotencyKey()
    },
    body: JSON.stringify({ title: "Proyecto T18" })
  });

  assert.equal(response.status, 201);
  const payload = await response.json();
  return payload.projectId;
}

test("Rutas D1 rechazan requests sin X-Session-Id", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-session-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const projectId = await createProject(server.baseUrl);
    const checks = [
      { method: "GET", url: `${server.baseUrl}/projects` },
      {
        method: "POST",
        url: `${server.baseUrl}/projects`,
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ title: "Sin sesion" })
      },
      {
        method: "POST",
        url: `${server.baseUrl}/projects/${projectId}/prompts`,
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ prompt: "Genera una landing" })
      },
      { method: "GET", url: `${server.baseUrl}/projects/${projectId}/messages` },
      { method: "GET", url: `${server.baseUrl}/projects/${projectId}/versions` },
      { method: "GET", url: `${server.baseUrl}/projects/${projectId}/preview?target=current` }
    ];

    for (const check of checks) {
      const response = await fetch(check.url, {
        method: check.method,
        headers: check.headers,
        body: check.body
      });
      const body = await response.json();

      assert.equal(response.status, 401);
      assert.equal(body.error.code, "SESSION_REQUIRED");
    }
  } finally {
    await server.close();
  }
});

test("Rutas D1 rechazan X-Session-Id invalido", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-session-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const response = await fetch(`${server.baseUrl}/projects`, {
      method: "GET",
      headers: { "x-session-id": "invalid-session-id" }
    });
    const body = await response.json();

    assert.equal(response.status, 401);
    assert.equal(body.error.code, "SESSION_REQUIRED");
  } finally {
    await server.close();
  }
});
