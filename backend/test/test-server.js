import { createApp } from "../src/http-app.js";
import { createDatabase } from "../src/db.js";
import { wrapSqliteSyncDatabase } from "../src/database-connection.js";

/** Arranca el HTTP server de tests. `db` es SQLite síncrono para aserciones en tests. */
export async function startTestServer(dbPath, appOptions = {}) {
  const syncDb = createDatabase(dbPath);
  const db = wrapSqliteSyncDatabase(syncDb);
  const app = createApp({ db, ...appOptions });

  await new Promise((resolve) => app.listen(0, resolve));
  const address = app.address();

  return {
    db: syncDb,
    baseUrl: `http://127.0.0.1:${address.port}`,
    close: () => new Promise((resolve) => app.close(resolve))
  };
}
