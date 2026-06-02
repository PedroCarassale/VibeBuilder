import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { createApp } from "../src/http-app.js";
import { createDatabase } from "../src/db.js";
import { createMockV0Provider } from "../src/generation-provider.js";
import { createSessionV0KeyStore } from "../src/session-v0-key-store.js";

const SESSION_ID = "8d6d3a2c-8d6a-4bf2-a0cf-f77a45ef27ab";

async function startServer(dbPath, options = {}) {
  const db = createDatabase(dbPath);
  const app = createApp({
    db,
    generationProvider: options.generationProvider ?? createMockV0Provider(),
    sessionV0KeyStore: options.sessionV0KeyStore ?? null,
    v0ApiBaseUrl: options.v0ApiBaseUrl
  });
  await new Promise((resolve) => app.listen(0, resolve));
  const address = app.address();
  return {
    baseUrl: `http://127.0.0.1:${address.port}`,
    close: () => new Promise((resolve) => app.close(resolve))
  };
}

test("PUT /integrations/v0 sin keystore devuelve 503", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-integ-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startServer(dbPath, { sessionV0KeyStore: null });

  try {
    const response = await fetch(`${server.baseUrl}/integrations/v0`, {
      method: "PUT",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID
      },
      body: JSON.stringify({ apiKey: "any" })
    });
    assert.equal(response.status, 503);
    const body = await response.json();
    assert.equal(body.error.code, "KEYSTORE_UNAVAILABLE");
  } finally {
    await server.close();
  }
});

test("GET/PUT/DELETE /integrations/v0 con almacenamiento de sesión", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-integ-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const db = createDatabase(dbPath);
  const sessionV0KeyStore = createSessionV0KeyStore({
    db,
    keystoreSecret: "integration-test-secret-16"
  });
  const server = await startServer(dbPath, { sessionV0KeyStore });

  try {
    let response = await fetch(`${server.baseUrl}/integrations/v0`, {
      method: "GET",
      headers: { "x-session-id": SESSION_ID }
    });
    let body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(body.keyStorageAvailable, true);
    assert.equal(body.sessionKeyConfigured, false);

    response = await fetch(`${server.baseUrl}/integrations/v0`, {
      method: "PUT",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID
      },
      body: JSON.stringify({ apiKey: "vcp_unit_test_placeholder_key" })
    });
    assert.equal(response.status, 204);

    response = await fetch(`${server.baseUrl}/integrations/v0`, {
      method: "GET",
      headers: { "x-session-id": SESSION_ID }
    });
    body = await response.json();
    assert.equal(body.sessionKeyConfigured, true);
    assert.equal(typeof body.sessionKeyHint, "string");

    response = await fetch(`${server.baseUrl}/integrations/v0`, {
      method: "DELETE",
      headers: { "x-session-id": SESSION_ID }
    });
    assert.equal(response.status, 204);

    response = await fetch(`${server.baseUrl}/integrations/v0`, {
      method: "GET",
      headers: { "x-session-id": SESSION_ID }
    });
    body = await response.json();
    assert.equal(body.sessionKeyConfigured, false);
  } finally {
    await server.close();
  }
});
