import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { createApp } from "../src/app.js";
import { createDatabase } from "../src/db.js";
import { createV0Provider } from "../src/generation-provider.js";

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

  return {
    db,
    baseUrl: `http://127.0.0.1:${address.port}`,
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
    body: JSON.stringify({ title: "Proyecto v0 real" })
  });

  assert.equal(response.status, 201);
  return response.json();
}

function readVersionRow(db, projectVersionId) {
  return db
    .prepare(
      "SELECT id, status, provider_meta FROM project_versions WHERE id = ?"
    )
    .get(projectVersionId);
}

test("POST /projects/:id/prompts integra v0 (mockeado) y persiste providerMeta sin secretos", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-v0-"));
  const dbPath = path.join(tmpDir, "db.sqlite");

  const fakeClient = {
    chats: {
      create: async ({ message }) => {
        assert.equal(message, "Genera un dashboard");
        return {
          id: "chat-real-1",
          object: "chat",
          webUrl: "https://v0.dev/chat/chat-real-1",
          apiUrl: "https://api.v0.dev/v1/chats/chat-real-1",
          authorId: "user-secret-id",
          // Estos campos sensibles NO deben filtrarse al cliente final.
          apiKey: "leak-attempt",
          token: "another-leak-attempt",
          latestVersion: {
            id: "version-real-1",
            object: "version",
            status: "completed",
            demoUrl: "https://preview.v0.dev/real-1"
          },
          modelConfiguration: { modelId: "v0-1.5-md" }
        };
      }
    }
  };

  const provider = createV0Provider({ client: fakeClient });
  const server = await startTestServer(dbPath, { generationProvider: provider });

  try {
    const { projectId } = await createProject(server.baseUrl);

    const response = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID
      },
      body: JSON.stringify({ prompt: "Genera un dashboard" })
    });

    assert.equal(response.status, 201);
    const body = await response.json();

    assert.equal(body.status, "success");
    assert.equal(body.providerMeta.provider, "v0");
    assert.equal(body.providerMeta.requestId, "chat-real-1");
    assert.equal(body.providerMeta.model, "v0-1.5-md");
    assert.equal(body.providerMeta.finishReason, "completed");
    assert.equal(body.providerMeta.previewUrl, "https://preview.v0.dev/real-1");
    assert.equal(body.providerMeta.promptLength, "Genera un dashboard".length);
    assert.equal(typeof body.providerMeta.latencyMs, "number");

    assert.equal("apiKey" in body.providerMeta, false);
    assert.equal("token" in body.providerMeta, false);
    assert.equal("authorId" in body.providerMeta, false);
    assert.equal("webUrl" in body.providerMeta, false);

    const versionRow = readVersionRow(server.db, body.projectVersionId);
    const persistedMeta = JSON.parse(versionRow.provider_meta);
    assert.deepEqual(persistedMeta, body.providerMeta);
    assert.equal("apiKey" in persistedMeta, false);
    assert.equal("token" in persistedMeta, false);
  } finally {
    await server.close();
  }
});

test("POST /projects/:id/prompts mapea error 401 de v0 a PROVIDER_UNAUTHORIZED no retryable", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-v0-"));
  const dbPath = path.join(tmpDir, "db.sqlite");

  const fakeClient = {
    chats: {
      create: async () => {
        throw new Error("HTTP 401: invalid api key");
      }
    }
  };
  const provider = createV0Provider({ client: fakeClient });
  const server = await startTestServer(dbPath, { generationProvider: provider });

  try {
    const { projectId } = await createProject(server.baseUrl);

    const response = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID
      },
      body: JSON.stringify({ prompt: "x" })
    });

    assert.equal(response.status, 201);
    const body = await response.json();

    assert.equal(body.status, "failed");
    assert.deepEqual(body.providerMeta, {
      provider: "v0",
      errorType: "provider_error",
      errorCode: "PROVIDER_UNAUTHORIZED",
      httpStatus: 401,
      retryable: false
    });

    const versionRow = readVersionRow(server.db, body.projectVersionId);
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

test("POST /projects/:id/prompts mapea error 429 de v0 a PROVIDER_RATE_LIMITED retryable", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-v0-"));
  const dbPath = path.join(tmpDir, "db.sqlite");

  const fakeClient = {
    chats: {
      create: async () => {
        throw new Error("HTTP 429: too many requests");
      }
    }
  };
  const provider = createV0Provider({ client: fakeClient });
  const server = await startTestServer(dbPath, { generationProvider: provider });

  try {
    const { projectId } = await createProject(server.baseUrl);

    const response = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID
      },
      body: JSON.stringify({ prompt: "x" })
    });

    const body = await response.json();
    assert.equal(body.status, "failed");
    assert.equal(body.providerMeta.errorCode, "PROVIDER_RATE_LIMITED");
    assert.equal(body.providerMeta.httpStatus, 429);
    assert.equal(body.providerMeta.retryable, true);
  } finally {
    await server.close();
  }
});

test("POST /projects/:id/prompts marca timeout cuando v0 cuelga", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-v0-"));
  const dbPath = path.join(tmpDir, "db.sqlite");

  const fakeClient = {
    chats: {
      create: () => new Promise(() => {})
    }
  };
  const provider = createV0Provider({ client: fakeClient });
  const server = await startTestServer(dbPath, {
    generationProvider: provider,
    generationTimeoutMs: 25
  });

  try {
    const { projectId } = await createProject(server.baseUrl);

    const response = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID
      },
      body: JSON.stringify({ prompt: "muy lento" })
    });

    const body = await response.json();
    assert.equal(body.status, "failed");
    assert.deepEqual(body.providerMeta, {
      provider: "v0",
      errorType: "timeout",
      errorCode: "PROVIDER_TIMEOUT",
      retryable: true
    });
  } finally {
    await server.close();
  }
});
