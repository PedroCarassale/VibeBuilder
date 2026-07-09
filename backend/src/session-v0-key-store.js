import {
  createCipheriv,
  createDecipheriv,
  createHash,
  randomBytes
} from "node:crypto";

const MAX_PLAIN_KEY_LENGTH = 512;

function deriveAes256Key(keystoreSecret) {
  return createHash("sha256")
    .update(`vibebuilder|session-v0|${keystoreSecret}`, "utf8")
    .digest();
}

function encryptGcm(key, plainUtf8) {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key, iv);
  const ciphertext = Buffer.concat([
    cipher.update(plainUtf8, "utf8"),
    cipher.final()
  ]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, tag, ciphertext]).toString("base64");
}

function decryptGcm(key, payloadB64) {
  const buf = Buffer.from(payloadB64, "base64");
  if (buf.length < 12 + 16) {
    throw new Error("Invalid ciphertext payload.");
  }
  const iv = buf.subarray(0, 12);
  const tag = buf.subarray(12, 28);
  const data = buf.subarray(28);
  const decipher = createDecipheriv("aes-256-gcm", key, iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(data), decipher.final()]).toString("utf8");
}

function makeKeyHint(trimmedKey) {
  if (trimmedKey.length <= 4) return "****";
  return `****${trimmedKey.slice(-4)}`;
}

function createEncryptedV0KeyStore({ db, keystoreSecret, tableName, idColumn }) {
  if (typeof keystoreSecret !== "string" || keystoreSecret.length < 16) {
    throw new Error("keystoreSecret must be a string with length >= 16.");
  }

  const key = deriveAes256Key(keystoreSecret);
  const selectStmt = db.prepare(`
    SELECT ciphertext, key_hint
    FROM ${tableName}
    WHERE ${idColumn} = ?;
  `);
  const upsertStmt = db.prepare(`
    INSERT INTO ${tableName} (${idColumn}, ciphertext, key_hint, updated_at)
    VALUES (?, ?, ?, ?)
    ON CONFLICT(${idColumn}) DO UPDATE SET
      ciphertext = excluded.ciphertext,
      key_hint = excluded.key_hint,
      updated_at = excluded.updated_at;
  `);
  const deleteStmt = db.prepare(`
    DELETE FROM ${tableName} WHERE ${idColumn} = ?;
  `);

  return {
    isEnabled: true,

    async getDecryptedApiKey(sessionId) {
      const row = await selectStmt.get(sessionId);
      if (!row?.ciphertext) return null;
      try {
        return decryptGcm(key, row.ciphertext);
      } catch {
        return null;
      }
    },

    async getStatus(sessionId) {
      const row = await selectStmt.get(sessionId);
      if (!row) {
        return { configured: false, keyHint: null };
      }
      return {
        configured: true,
        keyHint: typeof row.key_hint === "string" ? row.key_hint : null
      };
    },

    async save(sessionId, apiKey) {
      if (typeof apiKey !== "string") {
        throw new Error("apiKey must be a string.");
      }
      const trimmed = apiKey.trim();
      if (!trimmed) {
        throw new Error("apiKey must be non-empty.");
      }
      if (trimmed.length > MAX_PLAIN_KEY_LENGTH) {
        throw new Error(`apiKey exceeds max length (${MAX_PLAIN_KEY_LENGTH}).`);
      }
      const ciphertext = encryptGcm(key, trimmed);
      const hint = makeKeyHint(trimmed);
      await upsertStmt.run(sessionId, ciphertext, hint, new Date().toISOString());
    },

    async delete(sessionId) {
      await deleteStmt.run(sessionId);
    }
  };
}

/**
 * Almacena la API key de v0 por sesión, cifrada en reposo (AES-256-GCM).
 * Requiere `V0_KEYSTORE_SECRET` en el servidor; la key en claro solo existe en memoria al generar o guardar.
 */
export function createSessionV0KeyStore({ db, keystoreSecret }) {
  return createEncryptedV0KeyStore({
    db,
    keystoreSecret,
    tableName: "session_v0_keys",
    idColumn: "session_id"
  });
}

export function createUserV0KeyStore({ db, keystoreSecret }) {
  return createEncryptedV0KeyStore({
    db,
    keystoreSecret,
    tableName: "user_v0_keys",
    idColumn: "user_id"
  });
}
