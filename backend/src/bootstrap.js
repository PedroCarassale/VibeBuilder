import { config as loadEnv } from "dotenv";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { createApp } from "./app.js";
import { createDatabase } from "./db.js";
import { resolveGenerationProvider } from "./generation-provider.js";
import { createSessionV0KeyStore } from "./session-v0-key-store.js";

const envPath = resolve(fileURLToPath(new URL("../.env", import.meta.url)));

export function loadBackendEnv() {
  loadEnv({ path: envPath });
}

export function resolveDatabasePath(explicitPath) {
  if (typeof explicitPath === "string" && explicitPath.trim().length > 0) {
    return explicitPath.trim();
  }
  if (process.env.VERCEL === "1" || process.env.VERCEL_ENV) {
    return "/tmp/vibebuilder.db";
  }
  return undefined;
}

export function createBackendServer(options = {}) {
  const db = createDatabase(resolveDatabasePath(options.dbPath ?? process.env.DB_PATH));
  const generationProvider = options.generationProvider ?? resolveGenerationProvider();

  const keystoreSecret =
    typeof process.env.V0_KEYSTORE_SECRET === "string"
      ? process.env.V0_KEYSTORE_SECRET.trim()
      : "";
  let sessionV0KeyStore = null;
  if (keystoreSecret.length >= 16) {
    try {
      sessionV0KeyStore = createSessionV0KeyStore({ db, keystoreSecret });
    } catch (error) {
      console.warn("[bootstrap] Could not init session v0 key store:", error?.message ?? error);
    }
  }

  const v0ApiBaseUrl =
    typeof process.env.V0_API_URL === "string" && process.env.V0_API_URL.trim().length > 0
      ? process.env.V0_API_URL.trim()
      : undefined;

  const server = createApp({
    db,
    generationProvider,
    sessionV0KeyStore,
    v0ApiBaseUrl,
    ...options.appOptions
  });

  return { server, db, generationProvider };
}

let cachedServer = null;

/** Singleton HTTP server for Vercel serverless (reutiliza la misma instancia por contenedor). */
export function getOrCreateServer() {
  if (!cachedServer) {
    loadBackendEnv();
    cachedServer = createBackendServer().server;
  }
  return cachedServer;
}
