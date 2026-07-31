import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { startTestServer } from "./test-server.js";

const SESSION_OWNER = "8d6d3a2c-8d6a-4bf2-a0cf-f77a45ef27ab";
const SESSION_VISITOR = "c4ee8d66-0ec8-47fe-8e84-b4ed803f7253";
let sequence = 0;

async function createProject(baseUrl, sessionId, title = "Calculadora SaaS") {
  const response = await fetch(`${baseUrl}/projects`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-session-id": sessionId,
      "x-idempotency-key": `d3-create-${++sequence}`
    },
    body: JSON.stringify({
      title,
      description: "Proyecto base para compartir y remixar."
    })
  });
  assert.equal(response.status, 201);
  return (await response.json()).projectId;
}

function seedSuccessfulVersion(db, projectId) {
  db.prepare(`
    INSERT INTO project_versions (
      id,
      project_id,
      version_number,
      prompt_snapshot,
      status,
      preview_url,
      provider_meta,
      created_at
    ) VALUES (?, ?, 1, ?, 'success', ?, ?, ?);
  `).run(
    "source-version-1",
    projectId,
    "Crea una calculadora SaaS",
    "https://preview.example.com/source",
    JSON.stringify({ provider: "mock", previewUrl: "https://preview.example.com/source" }),
    "2026-01-01T10:00:00.000Z"
  );
  db.prepare(`
    INSERT INTO prompt_messages (
      id,
      project_id,
      version_id,
      role,
      content,
      created_at
    ) VALUES (?, ?, ?, 'user', ?, ?);
  `).run(
    "source-message-1",
    projectId,
    "source-version-1",
    "Crea una calculadora SaaS",
    "2026-01-01T10:00:00.000Z"
  );
  db.prepare("UPDATE projects SET current_version_id = ? WHERE id = ?;")
    .run("source-version-1", projectId);
}

async function publishProject(baseUrl, projectId) {
  const response = await fetch(`${baseUrl}/projects/${projectId}`, {
    method: "PATCH",
    headers: {
      "content-type": "application/json",
      "x-session-id": SESSION_OWNER
    },
    body: JSON.stringify({ visibility: "public" })
  });
  assert.equal(response.status, 200);
  return response.json();
}

test("Delivery 3: biblioteca publica lista solo proyectos publicados con atribucion y preview", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-d3-"));
  const server = await startTestServer(path.join(tmpDir, "db.sqlite"));
  try {
    const projectId = await createProject(server.baseUrl, SESSION_OWNER);
    seedSuccessfulVersion(server.db, projectId);

    const privateList = await fetch(`${server.baseUrl}/library/projects`, {
      headers: { "x-session-id": SESSION_VISITOR }
    });
    assert.equal(privateList.status, 200);
    assert.deepEqual(await privateList.json(), []);

    const published = await publishProject(server.baseUrl, projectId);
    assert.equal(published.visibility, "public");
    assert.ok(published.publishedAt);

    const list = await fetch(`${server.baseUrl}/library/projects`, {
      headers: { "x-session-id": SESSION_VISITOR }
    });
    assert.equal(list.status, 200);
    const projects = await list.json();
    assert.equal(projects.length, 1);
    assert.equal(projects[0].id, projectId);
    assert.equal(projects[0].ownerName, "Invitado");
    assert.equal(projects[0].currentVersionNumber, 1);
    assert.equal(projects[0].currentPreviewUrl, "https://preview.example.com/source");

    const detail = await fetch(`${server.baseUrl}/library/projects/${projectId}`, {
      headers: { "x-session-id": SESSION_VISITOR }
    });
    assert.equal(detail.status, 200);
    const detailBody = await detail.json();
    assert.equal(detailBody.title, "Calculadora SaaS");
    assert.equal(detailBody.versions.length, 1);
    assert.equal(detailBody.versions[0].promptSnapshot, "Crea una calculadora SaaS");
  } finally {
    await server.close();
  }
});

test("Delivery 3: fork copia ownership, atribucion, versiones, mensajes y permite iterar", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-d3-"));
  const server = await startTestServer(path.join(tmpDir, "db.sqlite"));
  try {
    const projectId = await createProject(server.baseUrl, SESSION_OWNER);
    seedSuccessfulVersion(server.db, projectId);

    const blockedFork = await fetch(`${server.baseUrl}/projects/${projectId}/fork`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_VISITOR,
        "x-idempotency-key": `d3-fork-${++sequence}`
      },
      body: "{}"
    });
    assert.equal(blockedFork.status, 404);

    await publishProject(server.baseUrl, projectId);
    const forkResponse = await fetch(`${server.baseUrl}/projects/${projectId}/fork`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_VISITOR,
        "x-idempotency-key": `d3-fork-${++sequence}`
      },
      body: "{}"
    });
    assert.equal(forkResponse.status, 201);
    const forkBody = await forkResponse.json();
    assert.equal(forkBody.originalProjectId, projectId);
    assert.equal(forkBody.originalProjectTitle, "Calculadora SaaS");

    const forkedProjectId = forkBody.projectId;
    const ownerCannotSeeFork = await fetch(`${server.baseUrl}/projects`, {
      headers: { "x-session-id": SESSION_OWNER }
    });
    assert.equal((await ownerCannotSeeFork.json()).some((project) => project.id === forkedProjectId), false);

    const visitorProjects = await fetch(`${server.baseUrl}/projects`, {
      headers: { "x-session-id": SESSION_VISITOR }
    });
    const visitorList = await visitorProjects.json();
    const forkedProject = visitorList.find((project) => project.id === forkedProjectId);
    assert.equal(forkedProject.title, "Fork de Calculadora SaaS");
    assert.equal(forkedProject.visibility, "private");
    assert.equal(forkedProject.originalProjectId, projectId);
    assert.equal(forkedProject.originalProjectTitle, "Calculadora SaaS");

    const forkVersions = await fetch(`${server.baseUrl}/projects/${forkedProjectId}/versions`, {
      headers: { "x-session-id": SESSION_VISITOR }
    });
    assert.equal(forkVersions.status, 200);
    assert.equal((await forkVersions.json())[0].promptSnapshot, "Crea una calculadora SaaS");

    const forkMessages = await fetch(`${server.baseUrl}/projects/${forkedProjectId}/messages`, {
      headers: { "x-session-id": SESSION_VISITOR }
    });
    assert.equal(forkMessages.status, 200);
    assert.equal((await forkMessages.json())[0].content, "Crea una calculadora SaaS");

    const iteration = await fetch(`${server.baseUrl}/projects/${forkedProjectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_VISITOR,
        "x-idempotency-key": `d3-iterate-${++sequence}`
      },
      body: JSON.stringify({ prompt: "Agrega una pantalla de pricing" })
    });
    assert.equal(iteration.status, 201);
    assert.equal((await iteration.json()).versionNumber, 2);

    const forkRow = server.db
      .prepare("SELECT original_project_id, forked_project_id, new_owner_session_id FROM forks WHERE forked_project_id = ?;")
      .get(forkedProjectId);
    assert.equal(forkRow.original_project_id, projectId);
    assert.equal(forkRow.forked_project_id, forkedProjectId);
    assert.equal(forkRow.new_owner_session_id, SESSION_VISITOR);
  } finally {
    await server.close();
  }
});
