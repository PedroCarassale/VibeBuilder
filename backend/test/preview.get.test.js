import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { startTestServer } from "./test-server.js";

const SESSION_ID = "8d6d3a2c-8d6a-4bf2-a0cf-f77a45ef27ab";
let idempotencySequence = 0;

function createIdempotencyKey() {
  idempotencySequence += 1;
  return `idem-preview-${idempotencySequence}`;
}

async function createProject(baseUrl, title = "Proyecto Preview") {
  const response = await fetch(`${baseUrl}/projects`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-session-id": SESSION_ID,
      "x-idempotency-key": createIdempotencyKey()
    },
    body: JSON.stringify({ title })
  });

  assert.equal(response.status, 201);
  return response.json();
}

function insertVersion(db, { id, projectId, versionNumber, status, previewUrl = null, providerMeta = {} }) {
  db.prepare(
    `
      INSERT INTO project_versions (
        id,
        project_id,
        version_number,
        prompt_snapshot,
        status,
        preview_url,
        provider_meta,
        created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
    `
  ).run(
    id,
    projectId,
    versionNumber,
    `Prompt ${versionNumber}`,
    status,
    previewUrl,
    JSON.stringify(providerMeta),
    "2026-01-01T10:00:00.000Z"
  );
}

test("GET /projects/:projectId/preview current sin version success retorna PREVIEW_NOT_READY", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-preview-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const { projectId } = await createProject(server.baseUrl);

    const response = await fetch(`${server.baseUrl}/projects/${projectId}/preview?target=current`, {
      headers: { "x-session-id": SESSION_ID }
    });

    assert.equal(response.status, 409);
    const body = await response.json();
    assert.equal(body.error.code, "PREVIEW_NOT_READY");
  } finally {
    await server.close();
  }
});

test("GET /projects/:projectId/preview version inexistente retorna VERSION_NOT_FOUND", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-preview-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const { projectId } = await createProject(server.baseUrl);

    const response = await fetch(
      `${server.baseUrl}/projects/${projectId}/preview?target=version&versionNumber=9`,
      {
        headers: { "x-session-id": SESSION_ID }
      }
    );

    assert.equal(response.status, 404);
    const body = await response.json();
    assert.equal(body.error.code, "VERSION_NOT_FOUND");
  } finally {
    await server.close();
  }
});

test("GET /projects/:projectId/preview current retorna 200 con preview_url canonico", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-preview-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const { projectId } = await createProject(server.baseUrl);
    insertVersion(server.db, {
      id: "version-1",
      projectId,
      versionNumber: 1,
      status: "success",
      previewUrl: "https://preview.v0.dev/abc-123"
    });
    server.db
      .prepare("UPDATE projects SET current_version_id = ? WHERE id = ?")
      .run("version-1", projectId);

    const response = await fetch(`${server.baseUrl}/projects/${projectId}/preview?target=current`, {
      headers: { "x-session-id": SESSION_ID }
    });

    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.projectId, projectId);
    assert.equal(body.target, "current");
    assert.equal(body.versionId, "version-1");
    assert.equal(body.versionNumber, 1);
    assert.equal(body.previewUrl, "https://preview.v0.dev/abc-123");
  } finally {
    await server.close();
  }
});

test("GET /projects/:projectId/preview version retorna 200 para version success especifica", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-preview-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const { projectId } = await createProject(server.baseUrl);
    insertVersion(server.db, {
      id: "version-1",
      projectId,
      versionNumber: 1,
      status: "success",
      previewUrl: "https://preview.v0.dev/version-1"
    });
    insertVersion(server.db, {
      id: "version-2",
      projectId,
      versionNumber: 2,
      status: "success",
      previewUrl: "https://preview.v0.dev/version-2"
    });
    server.db
      .prepare("UPDATE projects SET current_version_id = ? WHERE id = ?")
      .run("version-2", projectId);

    const response = await fetch(
      `${server.baseUrl}/projects/${projectId}/preview?target=version&versionNumber=1`,
      {
        headers: { "x-session-id": SESSION_ID }
      }
    );

    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.projectId, projectId);
    assert.equal(body.target, "version");
    assert.equal(body.versionId, "version-1");
    assert.equal(body.versionNumber, 1);
    assert.equal(body.previewUrl, "https://preview.v0.dev/version-1");
  } finally {
    await server.close();
  }
});

test("GET /projects/:projectId/preview current con preview expirada retorna PREVIEW_EXPIRED", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-preview-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const { projectId } = await createProject(server.baseUrl);
    insertVersion(server.db, {
      id: "version-1",
      projectId,
      versionNumber: 1,
      status: "success",
      previewUrl: "https://preview.v0.dev/expired",
      providerMeta: { previewState: "expired" }
    });
    server.db
      .prepare("UPDATE projects SET current_version_id = ? WHERE id = ?")
      .run("version-1", projectId);

    const response = await fetch(`${server.baseUrl}/projects/${projectId}/preview?target=current`, {
      headers: { "x-session-id": SESSION_ID }
    });

    assert.equal(response.status, 410);
    const body = await response.json();
    assert.equal(body.error.code, "PREVIEW_EXPIRED");
  } finally {
    await server.close();
  }
});

test("GET /projects/:projectId/preview current sin preview_url retorna PREVIEW_UNAVAILABLE", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-preview-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const { projectId } = await createProject(server.baseUrl);
    insertVersion(server.db, {
      id: "version-1",
      projectId,
      versionNumber: 1,
      status: "success",
      previewUrl: null,
      providerMeta: {}
    });
    server.db
      .prepare("UPDATE projects SET current_version_id = ? WHERE id = ?")
      .run("version-1", projectId);

    const response = await fetch(`${server.baseUrl}/projects/${projectId}/preview?target=current`, {
      headers: { "x-session-id": SESSION_ID }
    });

    assert.equal(response.status, 424);
    const body = await response.json();
    assert.equal(body.error.code, "PREVIEW_UNAVAILABLE");
  } finally {
    await server.close();
  }
});

test("GET /projects/:projectId/preview usa provider_meta.previewUrl como fallback", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-preview-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const { projectId } = await createProject(server.baseUrl);
    insertVersion(server.db, {
      id: "version-provider-meta",
      projectId,
      versionNumber: 1,
      status: "success",
      previewUrl: null,
      providerMeta: { previewUrl: "https://preview.v0.dev/provider-meta-url" }
    });
    server.db
      .prepare("UPDATE projects SET current_version_id = ? WHERE id = ?")
      .run("version-provider-meta", projectId);

    const response = await fetch(`${server.baseUrl}/projects/${projectId}/preview?target=current`, {
      headers: { "x-session-id": SESSION_ID }
    });

    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.previewUrl, "https://preview.v0.dev/provider-meta-url");
  } finally {
    await server.close();
  }
});
