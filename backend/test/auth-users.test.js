import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { startTestServer } from "./test-server.js";

const SESSION_ID = "8d6d3a2c-8d6a-4bf2-a0cf-f77a45ef27ab";
const OTHER_SESSION_ID = "11111111-1111-4111-8111-111111111111";

function createIdempotencyKey(label = "auth") {
  return `${label}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

async function register(baseUrl, { email = "ada@example.com", password = "correct-password", sessionId = SESSION_ID } = {}) {
  const response = await fetch(`${baseUrl}/auth/register`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-session-id": sessionId
    },
    body: JSON.stringify({ name: "Ada Lovelace", email, password })
  });
  const body = await response.json();
  assert.equal(response.status, 201);
  assert.ok(body.token);
  assert.equal(body.user.email, email.toLowerCase());
  return body;
}

async function login(baseUrl, { email = "ada@example.com", password = "correct-password", sessionId = SESSION_ID } = {}) {
  const response = await fetch(`${baseUrl}/auth/login`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-session-id": sessionId
    },
    body: JSON.stringify({ email, password })
  });
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.ok(body.token);
  return body;
}

async function createProject(baseUrl, { sessionId = SESSION_ID, token = null, title = "Proyecto" } = {}) {
  const response = await fetch(`${baseUrl}/projects`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-session-id": sessionId,
      "x-idempotency-key": createIdempotencyKey("project"),
      ...(token ? { authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ title })
  });
  const body = await response.json();
  assert.equal(response.status, 201);
  return body.projectId;
}

async function listProjects(baseUrl, { sessionId = SESSION_ID, token = null } = {}) {
  const response = await fetch(`${baseUrl}/projects`, {
    headers: {
      "x-session-id": sessionId,
      ...(token ? { authorization: `Bearer ${token}` } : {})
    }
  });
  const body = await response.json();
  assert.equal(response.status, 200);
  return body;
}

test("POST /auth/register crea usuario y rechaza email duplicado", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-auth-"));
  const server = await startTestServer(path.join(tmpDir, "db.sqlite"));

  try {
    const first = await register(server.baseUrl);
    const duplicate = await fetch(`${server.baseUrl}/auth/register`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID
      },
      body: JSON.stringify({
        name: "Ada",
        email: "ADA@example.com",
        password: "another-password"
      })
    });
    const body = await duplicate.json();

    assert.ok(first.user.id);
    assert.equal(duplicate.status, 409);
    assert.equal(body.error.code, "EMAIL_ALREADY_REGISTERED");

    const rows = server.db.prepare("SELECT id, email, password_hash FROM users").all();
    assert.equal(rows.length, 1);
    assert.equal(rows[0].email, "ada@example.com");
    assert.notEqual(rows[0].password_hash, "correct-password");
  } finally {
    await server.close();
  }
});

test("POST /auth/login crea nueva sesion con credenciales validas", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-auth-"));
  const server = await startTestServer(path.join(tmpDir, "db.sqlite"));

  try {
    const first = await register(server.baseUrl);
    const second = await login(server.baseUrl);

    assert.equal(second.user.id, first.user.id);
    assert.notEqual(second.token, first.token);
  } finally {
    await server.close();
  }
});

test("POST /auth/login rechaza credenciales invalidas", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-auth-"));
  const server = await startTestServer(path.join(tmpDir, "db.sqlite"));

  try {
    await register(server.baseUrl);
    const response = await fetch(`${server.baseUrl}/auth/login`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID
      },
      body: JSON.stringify({ email: "ada@example.com", password: "wrong-password" })
    });
    const body = await response.json();

    assert.equal(response.status, 401);
    assert.equal(body.error.code, "INVALID_AUTH_CREDENTIALS");
  } finally {
    await server.close();
  }
});

test("Bearer invalido devuelve 401", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-auth-"));
  const server = await startTestServer(path.join(tmpDir, "db.sqlite"));

  try {
    const response = await fetch(`${server.baseUrl}/projects`, {
      headers: {
        "x-session-id": SESSION_ID,
        authorization: "Bearer invalid-token"
      }
    });
    const body = await response.json();

    assert.equal(response.status, 401);
    assert.equal(body.error.code, "AUTH_REQUIRED");
  } finally {
    await server.close();
  }
});

test("proyectos guest y autenticados quedan aislados", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-auth-"));
  const server = await startTestServer(path.join(tmpDir, "db.sqlite"));

  try {
    const guestProjectId = await createProject(server.baseUrl, { title: "Guest" });
    const auth = await register(server.baseUrl);
    const userProjectId = await createProject(server.baseUrl, {
      token: auth.token,
      title: "Usuario"
    });

    const guestProjects = await listProjects(server.baseUrl);
    const userProjects = await listProjects(server.baseUrl, { token: auth.token });
    const otherGuestProjects = await listProjects(server.baseUrl, { sessionId: OTHER_SESSION_ID });

    assert.deepEqual(guestProjects.map((project) => project.id), [guestProjectId]);
    assert.deepEqual(userProjects.map((project) => project.id), [userProjectId]);
    assert.deepEqual(otherGuestProjects, []);
  } finally {
    await server.close();
  }
});

test("POST /auth/logout revoca token", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-auth-"));
  const server = await startTestServer(path.join(tmpDir, "db.sqlite"));

  try {
    const auth = await register(server.baseUrl);
    const logout = await fetch(`${server.baseUrl}/auth/logout`, {
      method: "POST",
      headers: { authorization: `Bearer ${auth.token}` }
    });
    assert.equal(logout.status, 204);

    const me = await fetch(`${server.baseUrl}/auth/me`, {
      headers: { authorization: `Bearer ${auth.token}` }
    });
    const body = await me.json();
    assert.equal(me.status, 401);
    assert.equal(body.error.code, "AUTH_REQUIRED");
  } finally {
    await server.close();
  }
});

test("key v0 de usuario tiene prioridad sobre key de sesion", async () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "vb-auth-"));
  const usedKeys = [];
  const server = await startTestServer(path.join(tmpDir, "db.sqlite"), {
    v0KeyStoreSecret: "test-secret-with-enough-length",
    sessionGenerationProviderFactory(apiKey) {
      usedKeys.push(apiKey);
      return {
        name: "mock-v0",
        async generate() {
          return {
            assistantText: "ok",
            providerMeta: {},
            artifactSource: {
              files: [
                { relativePath: "package.json", content: "{\"scripts\":{\"dev\":\"vite\"},\"dependencies\":{\"vite\":\"latest\",\"react\":\"latest\",\"react-dom\":\"latest\",\"typescript\":\"latest\"}}" },
                { relativePath: "index.html", content: "<div id=\"root\"></div>" },
                { relativePath: "src/main.tsx", content: "import React from 'react';" }
              ]
            }
          };
        }
      };
    }
  });

  try {
    await fetch(`${server.baseUrl}/integrations/v0`, {
      method: "PUT",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID
      },
      body: JSON.stringify({ apiKey: "session-key" })
    });

    const auth = await register(server.baseUrl);
    await fetch(`${server.baseUrl}/integrations/v0`, {
      method: "PUT",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        authorization: `Bearer ${auth.token}`
      },
      body: JSON.stringify({ apiKey: "user-key" })
    });

    const projectId = await createProject(server.baseUrl, {
      token: auth.token,
      title: "Con key user"
    });
    const prompt = await fetch(`${server.baseUrl}/projects/${projectId}/prompts`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-session-id": SESSION_ID,
        "x-idempotency-key": createIdempotencyKey("prompt"),
        authorization: `Bearer ${auth.token}`
      },
      body: JSON.stringify({ prompt: "Crea una landing" })
    });

    assert.equal(prompt.status, 201);
    assert.equal(usedKeys.at(-1), "user-key");
  } finally {
    await server.close();
  }
});
