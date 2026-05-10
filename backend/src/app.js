import { randomUUID } from "node:crypto";
import { createServer } from "node:http";
import {
  ProviderTimeoutError,
  V0ProviderError,
  createMockV0Provider,
  generateWithTimeout
} from "./generation-provider.js";

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
const INVALID_PROMPT_ERROR = {
  code: "INVALID_PROMPT",
  message: "prompt must be a non-empty string."
};
const PROJECT_NOT_FOUND_ERROR = {
  code: "PROJECT_NOT_FOUND",
  message: "Project not found."
};
const UUID_V4_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const DEFAULT_GENERATION_TIMEOUT_MS = 8_000;

function getValidSessionId(request, response) {
  const sessionId = request.headers[SESSION_ID_HEADER];
  if (typeof sessionId !== "string" || !UUID_V4_REGEX.test(sessionId)) {
    sendJson(response, 401, { error: SESSION_REQUIRED_ERROR });
    return null;
  }

  return sessionId;
}

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
    const sessionId = getValidSessionId(request, response);
    if (!sessionId) return;

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

function createListProjectsHandler(db) {
  const listProjectsStatement = db.prepare(`
    SELECT
      id,
      title,
      description,
      current_version_id,
      created_at,
      updated_at
    FROM projects
    WHERE session_id = ?
    ORDER BY updated_at DESC;
  `);

  return function handleListProjects(request, response) {
    const sessionId = getValidSessionId(request, response);
    if (!sessionId) return;

    const rows = listProjectsStatement.all(sessionId);
    const projects = rows.map((row) => ({
      id: row.id,
      title: row.title,
      description: row.description,
      currentVersionId: row.current_version_id,
      createdAt: row.created_at,
      updatedAt: row.updated_at
    }));

    sendJson(response, 200, projects);
  };
}

function createCreatePromptHandler(db) {
  const findProjectBySessionStatement = db.prepare(`
    SELECT id
    FROM projects
    WHERE id = ? AND session_id = ?;
  `);
  const getNextVersionNumberStatement = db.prepare(`
    SELECT COALESCE(MAX(version_number), 0) + 1 AS next_version_number
    FROM project_versions
    WHERE project_id = ?;
  `);
  const insertPromptMessageStatement = db.prepare(`
    INSERT INTO prompt_messages (
      id,
      project_id,
      version_id,
      role,
      content,
      created_at
    ) VALUES (?, ?, ?, ?, ?, ?);
  `);
  const insertProjectVersionStatement = db.prepare(`
    INSERT INTO project_versions (
      id,
      project_id,
      version_number,
      prompt_snapshot,
      status,
      provider_meta,
      created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?);
  `);
  const updateProjectCurrentVersionStatement = db.prepare(`
    UPDATE projects
    SET current_version_id = ?, updated_at = ?
    WHERE id = ?;
  `);

  return async function handleCreatePrompt(request, response, projectId, generationService) {
    const sessionId = getValidSessionId(request, response);
    if (!sessionId) return;

    const project = findProjectBySessionStatement.get(projectId, sessionId);
    if (!project) {
      sendJson(response, 404, { error: PROJECT_NOT_FOUND_ERROR });
      return;
    }

    let body;
    try {
      body = await parseJsonBody(request);
    } catch {
      sendJson(response, 400, { error: INVALID_JSON_ERROR });
      return;
    }

    const prompt = typeof body.prompt === "string" ? body.prompt.trim() : "";
    if (!prompt) {
      sendJson(response, 400, { error: INVALID_PROMPT_ERROR });
      return;
    }

    const promptMessageId = randomUUID();
    const projectVersionId = randomUUID();
    const now = new Date().toISOString();
    let status = "success";
    let providerMeta = null;

    try {
      const generationResult = await generateWithTimeout({
        provider: generationService.provider,
        payload: {
          projectId,
          prompt,
          sessionId
        },
        timeoutMs: generationService.timeoutMs
      });

      providerMeta = normalizeProviderMeta(generationResult.providerMeta, generationService.provider.name);
    } catch (error) {
      status = "failed";
      providerMeta = mapProviderErrorMeta(error, generationService.provider.name);
    }

    let versionNumber;
    try {
      const nextVersionRow = getNextVersionNumberStatement.get(projectId);
      versionNumber = nextVersionRow.next_version_number;

      db.exec("BEGIN;");
      insertProjectVersionStatement.run(
        projectVersionId,
        projectId,
        versionNumber,
        prompt,
        status,
        JSON.stringify(providerMeta),
        now
      );
      insertPromptMessageStatement.run(
        promptMessageId,
        projectId,
        projectVersionId,
        "user",
        prompt,
        now
      );
      if (status === "success") {
        updateProjectCurrentVersionStatement.run(projectVersionId, now, projectId);
      }
      db.exec("COMMIT;");
    } catch {
      db.exec("ROLLBACK;");
      sendJson(response, 500, {
        error: {
          code: "INTERNAL_ERROR",
          message: "Could not create prompt and version."
        }
      });
      return;
    }

    sendJson(response, 201, {
      promptMessageId,
      projectVersionId,
      versionNumber,
      status,
      providerMeta
    });
  };
}

function createListProjectMessagesHandler(db) {
  const findProjectBySessionStatement = db.prepare(`
    SELECT id
    FROM projects
    WHERE id = ? AND session_id = ?;
  `);
  const listMessagesStatement = db.prepare(`
    SELECT
      m.id,
      m.project_id,
      m.version_id,
      m.role,
      m.content,
      m.created_at,
      v.version_number
    FROM prompt_messages m
    LEFT JOIN project_versions v ON v.id = m.version_id
    WHERE m.project_id = ?
    ORDER BY m.created_at ASC, m.id ASC;
  `);

  return function handleListProjectMessages(request, response, projectId) {
    const sessionId = getValidSessionId(request, response);
    if (!sessionId) return;

    const project = findProjectBySessionStatement.get(projectId, sessionId);
    if (!project) {
      sendJson(response, 404, { error: PROJECT_NOT_FOUND_ERROR });
      return;
    }

    const rows = listMessagesStatement.all(projectId);
    const messages = rows.map((row) => ({
      id: row.id,
      projectId: row.project_id,
      versionId: row.version_id,
      role: row.role,
      content: row.content,
      createdAt: row.created_at,
      versionNumber: row.version_number
    }));
    sendJson(response, 200, messages);
  };
}

function createListProjectVersionsHandler(db) {
  const findProjectBySessionStatement = db.prepare(`
    SELECT id
    FROM projects
    WHERE id = ? AND session_id = ?;
  `);
  const listVersionsStatement = db.prepare(`
    SELECT
      id,
      project_id,
      version_number,
      prompt_snapshot,
      status,
      created_at
    FROM project_versions
    WHERE project_id = ?
    ORDER BY version_number DESC, created_at DESC;
  `);

  return function handleListProjectVersions(request, response, projectId) {
    const sessionId = getValidSessionId(request, response);
    if (!sessionId) return;

    const project = findProjectBySessionStatement.get(projectId, sessionId);
    if (!project) {
      sendJson(response, 404, { error: PROJECT_NOT_FOUND_ERROR });
      return;
    }

    const rows = listVersionsStatement.all(projectId);
    const versions = rows.map((row) => ({
      id: row.id,
      projectId: row.project_id,
      versionNumber: row.version_number,
      prompt: row.prompt_snapshot,
      status: row.status,
      previewUrl: null,
      createdAt: row.created_at
    }));
    sendJson(response, 200, versions);
  };
}

export function createApp({ db, generationProvider, generationTimeoutMs = DEFAULT_GENERATION_TIMEOUT_MS }) {
  const handleCreateProject = createProjectHandler(db);
  const handleListProjects = createListProjectsHandler(db);
  const handleCreatePrompt = createCreatePromptHandler(db);
  const handleListProjectMessages = createListProjectMessagesHandler(db);
  const handleListProjectVersions = createListProjectVersionsHandler(db);
  const generationService = {
    provider: generationProvider ?? createMockV0Provider(),
    timeoutMs: generationTimeoutMs
  };

  return createServer(async (request, response) => {
    const requestUrl = new URL(request.url, "http://localhost");
    const path = requestUrl.pathname;
    const createPromptMatch = /^\/projects\/([^/]+)\/prompts$/.exec(path);
    const listMessagesMatch = /^\/projects\/([^/]+)\/messages$/.exec(path);
    const listVersionsMatch = /^\/projects\/([^/]+)\/versions$/.exec(path);

    if (request.method === "POST" && path === "/projects") {
      await handleCreateProject(request, response);
      return;
    }

    if (request.method === "GET" && path === "/projects") {
      handleListProjects(request, response);
      return;
    }

    if (request.method === "POST" && createPromptMatch) {
      const projectId = createPromptMatch[1];
      await handleCreatePrompt(request, response, projectId, generationService);
      return;
    }

    if (request.method === "GET" && listMessagesMatch) {
      const projectId = listMessagesMatch[1];
      handleListProjectMessages(request, response, projectId);
      return;
    }

    if (request.method === "GET" && listVersionsMatch) {
      const projectId = listVersionsMatch[1];
      handleListProjectVersions(request, response, projectId);
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

function normalizeProviderMeta(providerMeta, providerName) {
  const normalized = {
    provider: typeof providerName === "string" ? providerName : "unknown"
  };

  if (!providerMeta || typeof providerMeta !== "object") {
    return normalized;
  }

  if (typeof providerMeta.model === "string") normalized.model = providerMeta.model;
  if (typeof providerMeta.requestId === "string") normalized.requestId = providerMeta.requestId;
  if (typeof providerMeta.finishReason === "string") normalized.finishReason = providerMeta.finishReason;
  if (typeof providerMeta.latencyMs === "number") normalized.latencyMs = providerMeta.latencyMs;
  if (typeof providerMeta.promptLength === "number") normalized.promptLength = providerMeta.promptLength;
  if (typeof providerMeta.previewUrl === "string") normalized.previewUrl = providerMeta.previewUrl;

  return normalized;
}

function mapProviderErrorMeta(error, providerName) {
  if (error instanceof ProviderTimeoutError) {
    return {
      provider: providerName,
      errorType: "timeout",
      errorCode: "PROVIDER_TIMEOUT",
      retryable: true
    };
  }

  if (error instanceof V0ProviderError) {
    const status = typeof error.status === "number" ? error.status : null;
    return {
      provider: providerName,
      errorType: "provider_error",
      errorCode: deriveV0ErrorCode(status, error.code),
      ...(status !== null ? { httpStatus: status } : {}),
      retryable: Boolean(error.retryable)
    };
  }

  return {
    provider: providerName,
    errorType: "provider_error",
    errorCode: "PROVIDER_ERROR",
    retryable: true
  };
}

function deriveV0ErrorCode(status, code) {
  if (typeof code === "string" && code.length > 0) {
    return code;
  }
  if (status === null) return "PROVIDER_ERROR";
  if (status === 401 || status === 403) return "PROVIDER_UNAUTHORIZED";
  if (status === 404) return "PROVIDER_NOT_FOUND";
  if (status === 408) return "PROVIDER_REQUEST_TIMEOUT";
  if (status === 429) return "PROVIDER_RATE_LIMITED";
  if (status >= 500) return "PROVIDER_UNAVAILABLE";
  if (status >= 400) return "PROVIDER_BAD_REQUEST";
  return "PROVIDER_ERROR";
}
