import { createHash, randomBytes, randomUUID, scrypt as scryptCallback, timingSafeEqual } from "node:crypto";
import { promisify } from "node:util";

const scrypt = promisify(scryptCallback);
const DEFAULT_AUTH_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const PASSWORD_KEY_LENGTH = 64;
const PASSWORD_HASH_VERSION = "scrypt-v1";
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const AUTH_REQUIRED_ERROR = {
  code: "AUTH_REQUIRED",
  message: "A valid Bearer token is required."
};

export const INVALID_AUTH_CREDENTIALS_ERROR = {
  code: "INVALID_AUTH_CREDENTIALS",
  message: "Email or password is invalid."
};

export const EMAIL_ALREADY_REGISTERED_ERROR = {
  code: "EMAIL_ALREADY_REGISTERED",
  message: "Email is already registered."
};

export const INVALID_REGISTER_BODY_ERROR = {
  code: "INVALID_REGISTER_BODY",
  message: "Body must include a valid email, password and optional name."
};

export const INVALID_LOGIN_BODY_ERROR = {
  code: "INVALID_LOGIN_BODY",
  message: "Body must include a valid email and password."
};

export function hashAuthToken(token) {
  return createHash("sha256").update(token, "utf8").digest("hex");
}

function createOpaqueToken() {
  return randomBytes(32).toString("base64url");
}

function normalizeEmail(email) {
  return typeof email === "string" ? email.trim().toLowerCase() : "";
}

function normalizeName(name) {
  if (typeof name !== "string") return null;
  const trimmed = name.trim();
  return trimmed.length > 0 ? trimmed.slice(0, 100) : null;
}

export function validateEmail(email) {
  const normalized = normalizeEmail(email);
  return normalized.length <= 254 && EMAIL_REGEX.test(normalized) ? normalized : null;
}

export function validatePassword(password) {
  return typeof password === "string" && password.length >= 8 && password.length <= 256;
}

async function hashPassword(password) {
  const salt = randomBytes(16).toString("base64url");
  const derived = await scrypt(password, salt, PASSWORD_KEY_LENGTH);
  return `${PASSWORD_HASH_VERSION}$${salt}$${Buffer.from(derived).toString("base64url")}`;
}

async function verifyPassword(password, storedHash) {
  if (typeof password !== "string" || typeof storedHash !== "string") return false;
  const [version, salt, expectedB64] = storedHash.split("$");
  if (version !== PASSWORD_HASH_VERSION || !salt || !expectedB64) return false;

  const expected = Buffer.from(expectedB64, "base64url");
  const actual = await scrypt(password, salt, expected.length);
  const actualBuffer = Buffer.from(actual);
  if (actualBuffer.length !== expected.length) return false;
  return timingSafeEqual(actualBuffer, expected);
}

function mapUser(row) {
  return {
    id: row.id,
    email: row.email,
    name: row.name,
    avatarUrl: row.avatar_url,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    lastLoginAt: row.last_login_at
  };
}

export function createAuthService({
  db,
  tokenTtlMs = DEFAULT_AUTH_TTL_MS
}) {
  const findUserByEmailStatement = db.prepare(`
    SELECT id, email, password_hash, name, avatar_url, created_at, updated_at, last_login_at
    FROM users
    WHERE email = ?;
  `);
  const insertUserStatement = db.prepare(`
    INSERT INTO users (
      id,
      email,
      password_hash,
      name,
      avatar_url,
      created_at,
      updated_at,
      last_login_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
  `);
  const updateLastLoginStatement = db.prepare(`
    UPDATE users
    SET last_login_at = ?, updated_at = ?
    WHERE id = ?
    RETURNING id, email, password_hash, name, avatar_url, created_at, updated_at, last_login_at;
  `);
  const insertSessionStatement = db.prepare(`
    INSERT INTO auth_sessions (
      id,
      user_id,
      token_hash,
      created_at,
      expires_at,
      revoked_at
    ) VALUES (?, ?, ?, ?, ?, NULL);
  `);
  const findSessionStatement = db.prepare(`
    SELECT
      s.id AS session_id,
      s.expires_at,
      s.revoked_at,
      u.id,
      u.email,
      u.name,
      u.avatar_url,
      u.created_at,
      u.updated_at,
      u.last_login_at
    FROM auth_sessions s
    INNER JOIN users u ON u.id = s.user_id
    WHERE s.token_hash = ?;
  `);
  const revokeSessionStatement = db.prepare(`
    UPDATE auth_sessions
    SET revoked_at = ?
    WHERE token_hash = ? AND revoked_at IS NULL;
  `);

  async function createSessionForUser(user) {
    const token = createOpaqueToken();
    const tokenHash = hashAuthToken(token);
    const now = new Date().toISOString();
    const expiresAt = new Date(Date.now() + tokenTtlMs).toISOString();
    await insertSessionStatement.run(randomUUID(), user.id, tokenHash, now, expiresAt);
    return {
      type: "signed_in",
      token,
      expiresAt,
      user: mapUser(user)
    };
  }

  async function register({ email, password, name }) {
    const normalizedEmail = validateEmail(email);
    if (!normalizedEmail || !validatePassword(password)) {
      return { type: "invalid_body" };
    }

    const existing = await findUserByEmailStatement.get(normalizedEmail);
    if (existing) {
      return { type: "email_exists" };
    }

    const now = new Date().toISOString();
    const userId = randomUUID();
    const passwordHash = await hashPassword(password);
    await insertUserStatement.run(
      userId,
      normalizedEmail,
      passwordHash,
      normalizeName(name),
      null,
      now,
      now,
      now
    );
    const user = await findUserByEmailStatement.get(normalizedEmail);
    return createSessionForUser(user);
  }

  async function login({ email, password }) {
    const normalizedEmail = validateEmail(email);
    if (!normalizedEmail || typeof password !== "string") {
      return { type: "invalid_body" };
    }

    const user = await findUserByEmailStatement.get(normalizedEmail);
    if (!user || !(await verifyPassword(password, user.password_hash))) {
      return { type: "invalid_credentials" };
    }

    const now = new Date().toISOString();
    const updated = await updateLastLoginStatement.get(now, now, user.id);
    return createSessionForUser(updated);
  }

  async function resolveBearerToken(token) {
    if (typeof token !== "string" || token.trim().length === 0) {
      return null;
    }

    const row = await findSessionStatement.get(hashAuthToken(token.trim()));
    if (!row || row.revoked_at) return null;
    if (new Date(row.expires_at).getTime() <= Date.now()) return null;

    return {
      token,
      sessionId: row.session_id,
      user: mapUser(row)
    };
  }

  async function revokeBearerToken(token) {
    if (typeof token !== "string" || token.trim().length === 0) return;
    await revokeSessionStatement.run(new Date().toISOString(), hashAuthToken(token.trim()));
  }

  return {
    register,
    login,
    resolveBearerToken,
    revokeBearerToken
  };
}
