import { createApp } from "./app.js";
import { createDatabase } from "./db.js";

const PORT = Number.parseInt(process.env.PORT ?? "3000", 10);
const dbPath = process.env.DB_PATH;

const db = createDatabase(dbPath);
const app = createApp({ db });

app.listen(PORT, () => {
  console.log(`VibeBuilder backend listening on port ${PORT}`);
});
