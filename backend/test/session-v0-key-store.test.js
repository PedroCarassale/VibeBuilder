import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { createDatabase } from "../src/db.js";
import { wrapSqliteSyncDatabase } from "../src/database-connection.js";
import { createSessionV0KeyStore } from "../src/session-v0-key-store.js";

test("session v0 key store cifra y recupera la key por sesión", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-v0-store-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const db = wrapSqliteSyncDatabase(createDatabase(dbPath));
  const store = createSessionV0KeyStore({
    db,
    keystoreSecret: "unit-test-secret-min-16"
  });

  const sessionId = "a1b2c3d4-e5f6-47a8-b9c0-d1e2f3a4b5c6";
  await store.save(sessionId, "  vcp_test_key_value  ");

  const st = await store.getStatus(sessionId);
  assert.equal(st.configured, true);
  assert.match(st.keyHint, /^.{4,}$/);

  assert.equal(await store.getDecryptedApiKey(sessionId), "vcp_test_key_value");

  await store.delete(sessionId);
  assert.equal(await store.getDecryptedApiKey(sessionId), null);
  assert.equal((await store.getStatus(sessionId)).configured, false);
});

test("createSessionV0KeyStore rechaza secreto corto", () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-v0-store-"));
  const dbPath = path.join(tmpDir, "db.sqlite");
  const db = createDatabase(dbPath);

  assert.throws(
    () =>
      createSessionV0KeyStore({
        db,
        keystoreSecret: "short"
      }),
    /length >= 16/
  );
});
