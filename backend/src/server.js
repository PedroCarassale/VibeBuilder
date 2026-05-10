import { createApp } from "./app.js";
import { createDatabase } from "./db.js";
import {
  resolveGenerationProvider,
  resolveGenerationTimeoutMs
} from "./generation-provider.js";

const PORT = Number.parseInt(process.env.PORT ?? "3000", 10);
const dbPath = process.env.DB_PATH;

const db = createDatabase(dbPath);
const generationProvider = resolveGenerationProvider();
const generationTimeoutMs = resolveGenerationTimeoutMs({ fallbackMs: 30_000 });

const app = createApp({ db, generationProvider, generationTimeoutMs });

app.listen(PORT, () => {
  console.log(
    `VibeBuilder backend listening on port ${PORT} ` +
      `(provider=${generationProvider.name}, timeoutMs=${generationTimeoutMs})`
  );
});
