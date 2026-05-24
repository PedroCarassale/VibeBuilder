import { config as loadEnv } from "dotenv";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { createApp } from "./app.js";
import { createDatabase } from "./db.js";
import { resolveGenerationProvider } from "./generation-provider.js";
import { createSessionV0KeyStore } from "./session-v0-key-store.js";

const envPath = resolve(fileURLToPath(new URL("../.env", import.meta.url)));
loadEnv({ path: envPath });

const PORT = Number.parseInt(process.env.PORT ?? "3000", 10);
const dbPath = process.env.DB_PATH;

const db = createDatabase(dbPath);
const generationProvider = resolveGenerationProvider();

const keystoreSecret =
  typeof process.env.V0_KEYSTORE_SECRET === "string" ? process.env.V0_KEYSTORE_SECRET.trim() : "";
let sessionV0KeyStore = null;
if (keystoreSecret.length >= 16) {
  try {
    sessionV0KeyStore = createSessionV0KeyStore({ db, keystoreSecret });
  } catch (error) {
    console.warn("[server] Could not init session v0 key store:", error?.message ?? error);
  }
}

const v0ApiBaseUrl =
  typeof process.env.V0_API_URL === "string" && process.env.V0_API_URL.trim().length > 0
    ? process.env.V0_API_URL.trim()
    : undefined;

const app = createApp({
  db,
  generationProvider,
  sessionV0KeyStore,
  v0ApiBaseUrl
});

app.listen(PORT, () => {
  console.log(
    `VibeBuilder backend listening on port ${PORT} (provider=${generationProvider.name})`
  );
});
