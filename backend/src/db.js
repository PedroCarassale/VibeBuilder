import fs from "node:fs";
import path from "node:path";
import { DatabaseSync } from "node:sqlite";

const DEFAULT_DB_PATH = path.resolve("backend/data/vibebuilder.db");

export function createDatabase(dbPath = DEFAULT_DB_PATH) {
  const resolvedPath = path.resolve(dbPath);
  const dbDirectory = path.dirname(resolvedPath);
  fs.mkdirSync(dbDirectory, { recursive: true });

  const db = new DatabaseSync(resolvedPath);
  db.exec(`
    CREATE TABLE IF NOT EXISTS projects (
      id TEXT PRIMARY KEY,
      session_id TEXT NOT NULL,
      title TEXT NOT NULL,
      description TEXT,
      current_version_id TEXT,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );

    CREATE INDEX IF NOT EXISTS idx_projects_session_id
      ON projects (session_id);

    CREATE TABLE IF NOT EXISTS project_versions (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL,
      version_number INTEGER NOT NULL,
      prompt_snapshot TEXT NOT NULL,
      status TEXT NOT NULL CHECK (status IN ('success', 'failed')),
      preview_url TEXT,
      provider_meta TEXT,
      created_at TEXT NOT NULL,
      FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
      UNIQUE(project_id, version_number)
    );

    CREATE INDEX IF NOT EXISTS idx_project_versions_project_id
      ON project_versions (project_id);
    CREATE UNIQUE INDEX IF NOT EXISTS idx_project_versions_project_version_unique
      ON project_versions (project_id, version_number);

    CREATE TABLE IF NOT EXISTS prompt_messages (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL,
      version_id TEXT,
      role TEXT NOT NULL,
      content TEXT NOT NULL,
      created_at TEXT NOT NULL,
      FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
      FOREIGN KEY (version_id) REFERENCES project_versions(id) ON DELETE SET NULL
    );

    CREATE INDEX IF NOT EXISTS idx_prompt_messages_project_id
      ON prompt_messages (project_id);

    CREATE TABLE IF NOT EXISTS idempotency_requests (
      session_id TEXT NOT NULL,
      endpoint TEXT NOT NULL,
      idempotency_key TEXT NOT NULL,
      payload_hash TEXT NOT NULL,
      response_status INTEGER,
      response_body TEXT,
      created_at TEXT NOT NULL,
      PRIMARY KEY (session_id, endpoint, idempotency_key)
    );

    CREATE TABLE IF NOT EXISTS telemetry_events (
      id TEXT PRIMARY KEY,
      event_name TEXT NOT NULL CHECK (
        event_name IN ('create', 'generate', 'fail', 'preview', 'iterate')
      ),
      session_id TEXT,
      project_id TEXT,
      version_id TEXT,
      metadata TEXT,
      created_at TEXT NOT NULL
    );

    CREATE INDEX IF NOT EXISTS idx_telemetry_events_created_at
      ON telemetry_events (created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_telemetry_events_project_id
      ON telemetry_events (project_id);
    CREATE INDEX IF NOT EXISTS idx_telemetry_events_version_id
      ON telemetry_events (version_id);

    CREATE TABLE IF NOT EXISTS session_v0_keys (
      session_id TEXT PRIMARY KEY,
      ciphertext TEXT NOT NULL,
      key_hint TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );
  `);

  const projectVersionColumns = db.prepare("PRAGMA table_info(project_versions);").all();
  const hasProviderMetaColumn = projectVersionColumns.some(
    (column) => column.name === "provider_meta"
  );
  const hasPreviewUrlColumn = projectVersionColumns.some(
    (column) => column.name === "preview_url"
  );

  if (!hasProviderMetaColumn) {
    db.exec("ALTER TABLE project_versions ADD COLUMN provider_meta TEXT;");
  }
  if (!hasPreviewUrlColumn) {
    db.exec("ALTER TABLE project_versions ADD COLUMN preview_url TEXT;");
  }

  db.exec(`
    CREATE UNIQUE INDEX IF NOT EXISTS idx_project_versions_project_version_unique
      ON project_versions (project_id, version_number);
  `);

  return db;
}
