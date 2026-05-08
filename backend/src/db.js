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
  `);

  return db;
}
