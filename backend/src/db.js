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
      provider_meta TEXT,
      created_at TEXT NOT NULL,
      FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
      UNIQUE(project_id, version_number)
    );

    CREATE INDEX IF NOT EXISTS idx_project_versions_project_id
      ON project_versions (project_id);

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
  `);

  const projectVersionColumns = db.prepare("PRAGMA table_info(project_versions);").all();
  const hasProviderMetaColumn = projectVersionColumns.some(
    (column) => column.name === "provider_meta"
  );

  if (!hasProviderMetaColumn) {
    db.exec("ALTER TABLE project_versions ADD COLUMN provider_meta TEXT;");
  }

  return db;
}
