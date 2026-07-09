import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { DatabaseSync } from "node:sqlite";

const DEFAULT_DB_PATH = path.resolve(
  fileURLToPath(new URL("../data/vibebuilder.db", import.meta.url))
);

export function createDatabase(dbPath = DEFAULT_DB_PATH) {
  const resolvedPath = path.resolve(dbPath);
  const dbDirectory = path.dirname(resolvedPath);
  fs.mkdirSync(dbDirectory, { recursive: true });

  const db = new DatabaseSync(resolvedPath);
  db.exec(`
    CREATE TABLE IF NOT EXISTS projects (
      id TEXT PRIMARY KEY,
      session_id TEXT NOT NULL,
      user_id TEXT,
      title TEXT NOT NULL,
      description TEXT,
      current_version_id TEXT,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      deleted_at TEXT,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
    );

    CREATE INDEX IF NOT EXISTS idx_projects_session_id
      ON projects (session_id);
    CREATE INDEX IF NOT EXISTS idx_projects_user_id
      ON projects (user_id);

    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      email TEXT NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      name TEXT,
      avatar_url TEXT,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      last_login_at TEXT NOT NULL
    );

    CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email
      ON users (email);

    CREATE TABLE IF NOT EXISTS auth_sessions (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL,
      token_hash TEXT NOT NULL UNIQUE,
      created_at TEXT NOT NULL,
      expires_at TEXT NOT NULL,
      revoked_at TEXT,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE INDEX IF NOT EXISTS idx_auth_sessions_token_hash
      ON auth_sessions (token_hash);
    CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_id
      ON auth_sessions (user_id);

    CREATE TABLE IF NOT EXISTS project_versions (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL,
      version_number INTEGER NOT NULL,
      prompt_snapshot TEXT NOT NULL,
      status TEXT NOT NULL CHECK (
        status IN ('queued', 'generating', 'validating', 'success', 'failed', 'cancelled')
      ),
      preview_url TEXT,
      provider_meta TEXT,
      source_version_id TEXT,
      attempt_number INTEGER NOT NULL DEFAULT 1,
      failure_code TEXT,
      started_at TEXT,
      completed_at TEXT,
      created_at TEXT NOT NULL,
      FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
      FOREIGN KEY (source_version_id) REFERENCES project_versions(id) ON DELETE SET NULL,
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

    CREATE TABLE IF NOT EXISTS user_v0_keys (
      user_id TEXT PRIMARY KEY,
      ciphertext TEXT NOT NULL,
      key_hint TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS version_artifacts (
      id TEXT PRIMARY KEY,
      version_id TEXT NOT NULL UNIQUE,
      framework TEXT NOT NULL,
      template_version TEXT NOT NULL,
      entry_point TEXT NOT NULL,
      generator_name TEXT,
      generator_version TEXT,
      provider_chat_id TEXT,
      provider_version_id TEXT,
      validation_status TEXT NOT NULL CHECK (
        validation_status IN ('structural_ok', 'structural_failed')
      ),
      validation_report TEXT,
      preview_ref TEXT,
      dependency_manifest TEXT,
      file_count INTEGER NOT NULL,
      total_bytes INTEGER NOT NULL,
      finalized_at TEXT NOT NULL,
      created_at TEXT NOT NULL,
      FOREIGN KEY (version_id) REFERENCES project_versions(id) ON DELETE CASCADE
    );

    CREATE INDEX IF NOT EXISTS idx_version_artifacts_version_id
      ON version_artifacts (version_id);

    CREATE TABLE IF NOT EXISTS version_artifact_files (
      id TEXT PRIMARY KEY,
      artifact_id TEXT NOT NULL,
      relative_path TEXT NOT NULL,
      size_bytes INTEGER NOT NULL,
      checksum_sha256 TEXT NOT NULL,
      storage_key TEXT NOT NULL,
      content_type TEXT,
      created_at TEXT NOT NULL,
      FOREIGN KEY (artifact_id) REFERENCES version_artifacts(id) ON DELETE CASCADE,
      UNIQUE(artifact_id, relative_path)
    );

    CREATE INDEX IF NOT EXISTS idx_version_artifact_files_artifact_id
      ON version_artifact_files (artifact_id);
  `);

  const projectVersionColumns = db.prepare("PRAGMA table_info(project_versions);").all();
  const projectVersionsSchema = db
    .prepare("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'project_versions';")
    .get();
  const projectVersionsSql = projectVersionsSchema?.sql ?? "";
  const hasExpandedStatusCheck =
    projectVersionsSql.includes("'queued'") &&
    projectVersionsSql.includes("'generating'") &&
    projectVersionsSql.includes("'validating'") &&
    projectVersionsSql.includes("'cancelled'");
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

  const refreshedProjectVersionColumns = db.prepare("PRAGMA table_info(project_versions);").all();
  const hasRegenerationColumns = ["source_version_id", "attempt_number", "failure_code", "started_at", "completed_at"].every(
    (name) => refreshedProjectVersionColumns.some((column) => column.name === name)
  );
  if (!hasExpandedStatusCheck || !hasRegenerationColumns) {
    db.exec(`
      PRAGMA foreign_keys = OFF;
      CREATE TABLE project_versions_new (
        id TEXT PRIMARY KEY,
        project_id TEXT NOT NULL,
        version_number INTEGER NOT NULL,
        prompt_snapshot TEXT NOT NULL,
        status TEXT NOT NULL CHECK (
          status IN ('queued', 'generating', 'validating', 'success', 'failed', 'cancelled')
        ),
        preview_url TEXT,
        provider_meta TEXT,
        source_version_id TEXT,
        attempt_number INTEGER NOT NULL DEFAULT 1,
        failure_code TEXT,
        started_at TEXT,
        completed_at TEXT,
        created_at TEXT NOT NULL,
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
        FOREIGN KEY (source_version_id) REFERENCES project_versions(id) ON DELETE SET NULL,
        UNIQUE(project_id, version_number)
      );
      INSERT INTO project_versions_new (
        id,
        project_id,
        version_number,
        prompt_snapshot,
        status,
        preview_url,
        provider_meta,
        source_version_id,
        attempt_number,
        failure_code,
        started_at,
        completed_at,
        created_at
      )
      SELECT
        id,
        project_id,
        version_number,
        prompt_snapshot,
        CASE
          WHEN status IN ('queued', 'generating', 'validating', 'success', 'failed', 'cancelled') THEN status
          ELSE 'failed'
        END,
        preview_url,
        provider_meta,
        NULL,
        1,
        CASE
          WHEN provider_meta IS NOT NULL AND json_valid(provider_meta) THEN json_extract(provider_meta, '$.errorCode')
          ELSE NULL
        END,
        created_at,
        created_at,
        created_at
      FROM project_versions;
      DROP TABLE project_versions;
      ALTER TABLE project_versions_new RENAME TO project_versions;
      PRAGMA foreign_keys = ON;
    `);
  }

  const projectColumns = db.prepare("PRAGMA table_info(projects);").all();
  const hasDeletedAtColumn = projectColumns.some((column) => column.name === "deleted_at");
  if (!hasDeletedAtColumn) {
    db.exec("ALTER TABLE projects ADD COLUMN deleted_at TEXT;");
  }
  const hasUserIdColumn = projectColumns.some((column) => column.name === "user_id");
  if (!hasUserIdColumn) {
    db.exec("ALTER TABLE projects ADD COLUMN user_id TEXT;");
  }

  const userColumns = db.prepare("PRAGMA table_info(users);").all();
  const hasPasswordHashColumn = userColumns.some((column) => column.name === "password_hash");
  if (!hasPasswordHashColumn) {
    db.exec("ALTER TABLE users ADD COLUMN password_hash TEXT;");
  }

  db.exec(`
    CREATE INDEX IF NOT EXISTS idx_projects_active_session_updated
      ON projects (session_id, updated_at DESC)
      WHERE deleted_at IS NULL AND user_id IS NULL;
    CREATE INDEX IF NOT EXISTS idx_projects_active_user_updated
      ON projects (user_id, updated_at DESC)
      WHERE deleted_at IS NULL AND user_id IS NOT NULL;
    CREATE INDEX IF NOT EXISTS idx_projects_user_id
      ON projects (user_id);
    CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email
      ON users (email);
    CREATE UNIQUE INDEX IF NOT EXISTS idx_project_versions_project_version_unique
      ON project_versions (project_id, version_number);
    CREATE INDEX IF NOT EXISTS idx_project_versions_source_version_id
      ON project_versions (source_version_id);
  `);

  return db;
}
