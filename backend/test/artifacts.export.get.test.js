import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { startTestServer } from "./test-server.js";
import { createLocalArtifactStorage } from "../src/artifacts/local-artifact-storage.js";

const SESSION_ID = "8d6d3a2c-8d6a-4bf2-a0cf-f77a45ef27ab";
let idempotencySequence = 0;

function createIdempotencyKey() {
  idempotencySequence += 1;
  return `idem-export-${idempotencySequence}`;
}

test("GET /projects/:id/versions/:n/export devuelve zip para owner", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-artifact-export-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const storageRoot = path.join(tmpDir, "artifacts");
  const server = await startTestServer(dbPath, {
    artifactStorage: createLocalArtifactStorage({ rootPath: storageRoot })
  });

  try {
    const createResponse = await fetch(`${server.baseUrl}/projects`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({ title: "Export test" })
    });
    const { projectId } = await createResponse.json();

    const promptResponse = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({ prompt: "Landing exportable" })
    });
    assert.equal(promptResponse.status, 201);

    const exportResponse = await fetch(
      `${server.baseUrl}/projects/${projectId}/versions/1/export`,
      { headers: { "x-session-id": SESSION_ID } }
    );

    assert.equal(exportResponse.status, 200);
    assert.match(exportResponse.headers.get("content-type") ?? "", /zip/);
    const buffer = Buffer.from(await exportResponse.arrayBuffer());
    assert.ok(buffer.length > 100);
    assert.equal(buffer[0], 0x50);
    assert.equal(buffer[1], 0x4b);
  } finally {
    await server.close();
  }
});

test("GET export oculta versiones ajenas como 404", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-artifact-export-404-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath, {
    artifactStorage: createLocalArtifactStorage({
      rootPath: path.join(tmpDir, "artifacts")
    })
  });

  try {
    const createResponse = await fetch(`${server.baseUrl}/projects`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({ title: "Private export" })
    });
    const { projectId } = await createResponse.json();

    await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({ prompt: "No compartir" })
    });

    const exportResponse = await fetch(
      `${server.baseUrl}/projects/${projectId}/versions/1/export`,
      { headers: { "x-session-id": "11111111-1111-4111-8111-111111111111" } }
    );
    assert.equal(exportResponse.status, 404);
  } finally {
    await server.close();
  }
});
