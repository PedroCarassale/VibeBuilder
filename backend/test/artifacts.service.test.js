import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { createDatabase } from "../src/db.js";
import { wrapSqliteSyncDatabase } from "../src/database-connection.js";
import { createLocalArtifactStorage } from "../src/artifacts/local-artifact-storage.js";
import { createArtifactService } from "../src/artifacts/artifact-service.js";
import { createMockArtifactSource } from "../src/artifacts/mock-artifact-source.js";

const SESSION_ID = "8d6d3a2c-8d6a-4bf2-a0cf-f77a45ef27ab";

function seedProject(syncDb, projectId) {
  const now = new Date().toISOString();
  syncDb
    .prepare(
      `INSERT INTO projects (id, session_id, title, description, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?)`
    )
    .run(projectId, SESSION_ID, "Artifact test", "", now, now);
}

test("artifact service persists and returns immutable summary", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-artifact-service-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const storageRoot = path.join(tmpDir, "artifacts");
  const syncDb = createDatabase(dbPath);
  const db = wrapSqliteSyncDatabase(syncDb);
  const artifactService = createArtifactService({
    db,
    artifactStorage: createLocalArtifactStorage({ rootPath: storageRoot })
  });

  const projectId = "project-artifact-1";
  const versionId = "version-artifact-1";
  seedProject(syncDb, projectId);

  const prepared = await artifactService.prepareArtifactPayload({
    projectId,
    sessionId: SESSION_ID,
    artifactSource: createMockArtifactSource({ prompt: "Dashboard" })
  });

  await db.transaction(async (tx) => {
    const now = new Date().toISOString();
    await tx
      .prepare(
        `INSERT INTO project_versions
         (id, project_id, version_number, prompt_snapshot, status, created_at)
         VALUES (?, ?, ?, ?, ?, ?)`
      )
      .run(versionId, projectId, 1, "prompt", "success", now);
    await artifactService.insertPreparedArtifact(tx, { versionId, prepared });
  });

  const detail = await artifactService.getVersionArtifactDetail({
    projectId,
    versionNumber: 1,
    sessionId: SESSION_ID
  });

  assert.equal(detail.framework, "react-vite-ts");
  assert.equal(detail.fileCount, 6);
  assert.ok(detail.files.length > 0);

  const summaries = await artifactService.getArtifactSummaryForVersions(projectId);
  assert.equal(summaries.get(versionId)?.artifact?.hasExport, true);
});

test("artifact service cleans up storage when db insert fails", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-artifact-cleanup-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const storageRoot = path.join(tmpDir, "artifacts");
  const syncDb = createDatabase(dbPath);
  const db = wrapSqliteSyncDatabase(syncDb);
  const artifactStorage = createLocalArtifactStorage({ rootPath: storageRoot });
  const artifactService = createArtifactService({ db, artifactStorage });

  const projectId = "project-artifact-2";
  seedProject(syncDb, projectId);

  const prepared = await artifactService.prepareArtifactPayload({
    projectId,
    sessionId: SESSION_ID,
    artifactSource: createMockArtifactSource({ prompt: "Cleanup" })
  });

  await assert.rejects(
    () =>
      db.transaction(async (tx) => {
        await artifactService.insertPreparedArtifact(tx, {
          versionId: "missing-version-fk",
          prepared
        });
      }),
    /FOREIGN KEY/
  );

  await artifactService.cleanupPreparedArtifact(prepared);
  assert.equal(fs.existsSync(path.join(storageRoot, prepared.storagePrefix)), false);
});
