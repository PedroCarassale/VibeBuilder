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
  return `idem-artifact-${idempotencySequence}`;
}

async function createProject(baseUrl) {
  const response = await fetch(`${baseUrl}/projects`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-session-id": SESSION_ID,
      "x-idempotency-key": createIdempotencyKey()
    },
    body: JSON.stringify({ title: "Artifact integration" })
  });
  assert.equal(response.status, 201);
  return response.json();
}

test("POST /projects/:id/prompts persiste artefacto en versiones exitosas", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-artifact-prompt-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const storageRoot = path.join(tmpDir, "artifacts");
  const server = await startTestServer(dbPath, {
    artifactStorage: createLocalArtifactStorage({ rootPath: storageRoot })
  });

  try {
    const { projectId } = await createProject(server.baseUrl);
    const response = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({ prompt: "Crea una landing" })
    });

    assert.equal(response.status, 201);
    const body = await response.json();
    assert.equal(body.status, "success");

    const artifactRow = server.db
      .prepare("SELECT version_id, file_count FROM version_artifacts WHERE version_id = ?")
      .get(body.projectVersionId);
    assert.ok(artifactRow);
    assert.ok(artifactRow.file_count >= 5);

    const versionsResponse = await fetch(`${server.baseUrl}/projects/${projectId}/versions`, {
      headers: { "x-session-id": SESSION_ID }
    });
    const versions = await versionsResponse.json();
    assert.equal(versions[0].artifact.framework, "react-vite-ts");
    assert.equal(versions[0].artifact.hasExport, true);
  } finally {
    await server.close();
  }
});

test("POST /projects/:id/prompts marca failed si el storage falla", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-artifact-fail-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const failingStorage = {
    name: "failing",
    async putFile() {
      throw new Error("storage unavailable");
    },
    async getFile() {
      throw new Error("storage unavailable");
    },
    openReadStream() {
      throw new Error("storage unavailable");
    },
    async deletePrefix() {}
  };

  const server = await startTestServer(dbPath, { artifactStorage: failingStorage });

  try {
    const { projectId } = await createProject(server.baseUrl);
    const response = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": createIdempotencyKey()
      },
      body: JSON.stringify({ prompt: "Debe fallar storage" })
    });

    assert.equal(response.status, 201);
    const body = await response.json();
    assert.equal(body.status, "failed");
    assert.equal(body.providerMeta.errorCode, "ARTIFACT_STORAGE_FAILED");

    const artifactCount = server.db
      .prepare("SELECT COUNT(*) AS count FROM version_artifacts")
      .get().count;
    assert.equal(artifactCount, 0);
  } finally {
    await server.close();
  }
});
