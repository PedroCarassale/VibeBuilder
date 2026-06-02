import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { createApp } from "../src/http-app.js";
import { createDatabase } from "../src/db.js";

const SESSION_ID = "8d6d3a2c-8d6a-4bf2-a0cf-f77a45ef27ab";
let idempotencySequence = 0;

function createIdempotencyKey() {
  idempotencySequence += 1;
  return `idem-telemetry-${idempotencySequence}`;
}

async function startTestServer(dbPath) {
  let generationAttempts = 0;
  const provider = {
    name: "mock-v0",
    async generate() {
      generationAttempts += 1;
      if (generationAttempts === 2) {
        throw new Error("provider fails on second prompt");
      }
      return {
        providerMeta: {
          model: "v0-simulated",
          requestId: `req-${generationAttempts}`,
          previewUrl: `https://preview.v0.dev/${generationAttempts}`
        }
      };
    }
  };

  const db = createDatabase(dbPath);
  const app = createApp({ db, generationProvider: provider });
  await new Promise((resolve) => app.listen(0, resolve));
  const address = app.address();
  const baseUrl = `http://127.0.0.1:${address.port}`;

  return {
    db,
    baseUrl,
    close: () => new Promise((resolve) => app.close(resolve))
  };
}

async function createProject(baseUrl) {
  const response = await fetch(`${baseUrl}/projects`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-session-id": SESSION_ID,
      "x-idempotency-key": createIdempotencyKey()
    },
    body: JSON.stringify({ title: "Proyecto telemetria" })
  });
  assert.equal(response.status, 201);
  return response.json();
}

async function createPrompt(baseUrl, projectId, prompt) {
  const response = await fetch(`${baseUrl}/projects/${projectId}/prompts`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-session-id": SESSION_ID,
      "x-idempotency-key": createIdempotencyKey()
    },
    body: JSON.stringify({ prompt })
  });
  assert.equal(response.status, 201);
  return response.json();
}

test("GET /telemetry/summary resume eventos create/generate/fail/preview/iterate", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-telemetry-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const { projectId } = await createProject(server.baseUrl);
    const firstPrompt = await createPrompt(
      server.baseUrl,
      projectId,
      "Genera una landing para una cafeteria"
    );
    assert.equal(firstPrompt.status, "success");

    const secondPrompt = await createPrompt(
      server.baseUrl,
      projectId,
      "Agrega login y panel de admin"
    );
    assert.equal(secondPrompt.status, "failed");

    const previewResponse = await fetch(`${server.baseUrl}/projects/${projectId}/preview?target=current`, {
      headers: { "x-session-id": SESSION_ID }
    });
    assert.equal(previewResponse.status, 200);

    const summaryResponse = await fetch(`${server.baseUrl}/telemetry/summary`, {
      headers: { "x-session-id": SESSION_ID }
    });
    assert.equal(summaryResponse.status, 200);
    const summary = await summaryResponse.json();

    assert.equal(summary.totals.create, 1);
    assert.equal(summary.totals.generate, 1);
    assert.equal(summary.totals.fail, 1);
    assert.equal(summary.totals.preview, 1);
    assert.equal(summary.totals.iterate, 2);
    assert.ok(Array.isArray(summary.recentEvents));
    assert.ok(summary.recentEvents.length >= 6);

    const hasCorrelatedGenerateEvent = summary.recentEvents.some(
      (event) =>
        event.eventName === "generate" &&
        event.projectId === projectId &&
        event.versionId === firstPrompt.projectVersionId
    );
    const hasCorrelatedFailEvent = summary.recentEvents.some(
      (event) =>
        event.eventName === "fail" &&
        event.projectId === projectId &&
        event.versionId === secondPrompt.projectVersionId
    );
    const hasCorrelatedPreviewEvent = summary.recentEvents.some(
      (event) => event.eventName === "preview" && event.projectId === projectId && event.versionId
    );
    assert.equal(hasCorrelatedGenerateEvent, true);
    assert.equal(hasCorrelatedFailEvent, true);
    assert.equal(hasCorrelatedPreviewEvent, true);

    const persistedTotals = server.db
      .prepare(
        `
          SELECT event_name, COUNT(*) AS total
          FROM telemetry_events
          WHERE session_id = ?
          GROUP BY event_name
          ORDER BY event_name ASC;
        `
      )
      .all(SESSION_ID)
      .map((row) => ({ event_name: row.event_name, total: row.total }));
    assert.deepEqual(persistedTotals, [
      { event_name: "create", total: 1 },
      { event_name: "fail", total: 1 },
      { event_name: "generate", total: 1 },
      { event_name: "iterate", total: 2 },
      { event_name: "preview", total: 1 }
    ]);
  } finally {
    await server.close();
  }
});

test("GET /telemetry/summary requiere X-Session-Id valido", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-backend-telemetry-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const server = await startTestServer(dbPath);

  try {
    const response = await fetch(`${server.baseUrl}/telemetry/summary`);
    assert.equal(response.status, 401);
    const body = await response.json();
    assert.equal(body.error.code, "SESSION_REQUIRED");
  } finally {
    await server.close();
  }
});
