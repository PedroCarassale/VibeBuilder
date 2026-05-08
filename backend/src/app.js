import { randomUUID } from "node:crypto";
import { createServer } from "node:http";

const SESSION_ID_HEADER = "x-session-id";
const SESSION_REQUIRED_ERROR = {
  code: "SESSION_REQUIRED",
  message: "A valid X-Session-Id header is required."
};
const INVALID_TITLE_ERROR = {
  code: "INVALID_TITLE",
  message: "title must be a non-empty string."
};
const INVALID_JSON_ERROR = {
  code: "INVALID_JSON",
  message: "Request body must be valid JSON."
};
const UUID_V4_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function sendJson(response, statusCode, body) {
  response.writeHead(statusCode, { "content-type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(body));
}

async function parseJsonBody(request) {
  const chunks = [];
  for await (const chunk of request) {
    chunks.push(chunk);
  }

  if (chunks.length === 0) {
    return {};
  }

  const rawBody = Buffer.concat(chunks).toString("utf8");
  if (!rawBody.trim()) {
    return {};
  }

  return JSON.parse(rawBody);
}

function normalizeDescription(value) {
  if (typeof value !== "string") {
    return null;
  }

  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
}

function createProjectHandler(db) {
  const insertProjectStatement = db.prepare(`
    INSERT INTO projects (
      id,
      session_id,
      title,
      description,
      current_version_id,
      created_at,
      updated_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?);
  `);

  return async function handleCreateProject(request, response) {
    const sessionId = request.headers[SESSION_ID_HEADER];
    if (typeof sessionId !== "string" || !UUID_V4_REGEX.test(sessionId)) {
      sendJson(response, 401, { error: SESSION_REQUIRED_ERROR });
      return;
    }

    let body;
    try {
      body = await parseJsonBody(request);
    } catch {
      sendJson(response, 400, { error: INVALID_JSON_ERROR });
      return;
    }

    const rawTitle = typeof body.title === "string" ? body.title.trim() : "";
    if (!rawTitle) {
      sendJson(response, 400, { error: INVALID_TITLE_ERROR });
      return;
    }

    const projectId = randomUUID();
    const now = new Date().toISOString();
    insertProjectStatement.run(
      projectId,
      sessionId,
      rawTitle,
      normalizeDescription(body.description),
      null,
      now,
      now
    );

    sendJson(response, 201, { projectId });
  };
}

export function createApp({ db }) {
  const handleCreateProject = createProjectHandler(db);

  return createServer(async (request, response) => {
    if (request.method === "POST" && request.url === "/projects") {
      await handleCreateProject(request, response);
      return;
    }

    sendJson(response, 404, {
      error: {
        code: "NOT_FOUND",
        message: "Route not found."
      }
    });
  });
}
