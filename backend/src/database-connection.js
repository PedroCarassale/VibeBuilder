import { createClient } from "@libsql/client";
import { createDatabase as createSyncSqliteDatabase } from "./db.js";
import { applyMigrations } from "./schema.js";

export function wrapSqliteSyncDatabase(syncDb) {
  return {
    kind: "sqlite",
    async exec(sql) {
      syncDb.exec(sql);
    },
    prepare(sql) {
      const statement = syncDb.prepare(sql);
      return {
        async run(...args) {
          statement.run(...args);
        },
        async all(...args) {
          return statement.all(...args);
        },
        async get(...args) {
          return statement.get(...args);
        }
      };
    },
    async transaction(fn) {
      syncDb.exec("BEGIN IMMEDIATE;");
      try {
        const result = await fn(this);
        syncDb.exec("COMMIT;");
        return result;
      } catch (error) {
        try {
          syncDb.exec("ROLLBACK;");
        } catch {
          // noop
        }
        throw error;
      }
    }
  };
}

function wrapLibsqlClient(client) {
  return {
    kind: "libsql",
    async exec(sql) {
      await client.execute(sql);
    },
    prepare(sql) {
      return {
        async run(...args) {
          await client.execute({ sql, args });
        },
        async all(...args) {
          const result = await client.execute({ sql, args });
          return result.rows;
        },
        async get(...args) {
          const result = await client.execute({ sql, args });
          return result.rows[0];
        }
      };
    },
    async transaction(fn) {
      const tx = await client.transaction("write");
      const txDb = {
        prepare(statementSql) {
          return {
            async run(...args) {
              await tx.execute({ sql: statementSql, args });
            },
            async all(...args) {
              const result = await tx.execute({ sql: statementSql, args });
              return result.rows;
            },
            async get(...args) {
              const result = await tx.execute({ sql: statementSql, args });
              return result.rows[0];
            }
          };
        }
      };

      try {
        const result = await fn(txDb);
        await tx.commit();
        return result;
      } catch (error) {
        await tx.rollback();
        throw error;
      }
    }
  };
}

export function isTursoConfigured() {
  const url = process.env.TURSO_DATABASE_URL;
  return typeof url === "string" && url.trim().length > 0;
}

export async function createDatabaseConnection(dbPath) {
  if (isTursoConfigured()) {
    const client = createClient({
      url: process.env.TURSO_DATABASE_URL.trim(),
      authToken:
        typeof process.env.TURSO_AUTH_TOKEN === "string"
          ? process.env.TURSO_AUTH_TOKEN.trim()
          : undefined
    });
    const db = wrapLibsqlClient(client);
    await applyMigrations(db);
    console.info("[database] Using Turso (libsql) for persistent storage.");
    return db;
  }

  if (process.env.VERCEL === "1" || process.env.VERCEL_ENV) {
    console.warn(
      "[database] TURSO_DATABASE_URL is not set. SQLite in /tmp is not shared across Vercel instances — projects may disappear between requests."
    );
  }

  const db = wrapSqliteSyncDatabase(createSyncSqliteDatabase(dbPath));
  await applyMigrations(db);
  return db;
}
