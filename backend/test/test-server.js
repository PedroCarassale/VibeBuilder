import { createApp } from "../src/http-app.js";
import { createDatabase } from "../src/db.js";
import { wrapSqliteSyncDatabase } from "../src/database-connection.js";
import { createSessionV0KeyStore, createUserV0KeyStore } from "../src/session-v0-key-store.js";
import { withMockArtifactResult } from "./helpers/mock-generation-provider.js";

function ensureArtifactAwareProvider(provider) {
  if (!provider || typeof provider.generate !== "function") {
    return provider;
  }

  const originalGenerate = provider.generate.bind(provider);
  return {
    ...provider,
    async generate(payload) {
      const result = await originalGenerate(payload);
      if (!result || result.artifactSource) {
        return result;
      }
      return withMockArtifactResult(result, payload?.prompt ?? "");
    }
  };
}

/** Arranca el HTTP server de tests. `db` es SQLite síncrono para aserciones en tests. */
export async function startTestServer(dbPath, appOptions = {}) {
  const syncDb = createDatabase(dbPath);
  const db = wrapSqliteSyncDatabase(syncDb);
  const normalizedOptions = {
    ...appOptions,
    generationProvider: appOptions.generationProvider
      ? ensureArtifactAwareProvider(appOptions.generationProvider)
      : undefined
  };
  if (typeof appOptions.v0KeyStoreSecret === "string") {
    normalizedOptions.sessionV0KeyStore = createSessionV0KeyStore({
      db,
      keystoreSecret: appOptions.v0KeyStoreSecret
    });
    normalizedOptions.userV0KeyStore = createUserV0KeyStore({
      db,
      keystoreSecret: appOptions.v0KeyStoreSecret
    });
    delete normalizedOptions.v0KeyStoreSecret;
  }
  const app = createApp({ db, ...normalizedOptions });

  await new Promise((resolve) => app.listen(0, resolve));
  const address = app.address();

  return {
    db: syncDb,
    baseUrl: `http://127.0.0.1:${address.port}`,
    close: () => new Promise((resolve) => app.close(resolve))
  };
}
