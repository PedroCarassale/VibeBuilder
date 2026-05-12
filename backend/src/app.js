import { createHash, randomUUID } from "node:crypto";
import { createServer } from "node:http";
import {
  ProviderTimeoutError,
  V0ProviderError,
  createMockV0Provider,
  generateWithTimeout
} from "./generation-provider.js";

const SESSION_ID_HEADER = "x-session-id";
const IDEMPOTENCY_KEY_HEADER = "x-idempotency-key";
const SESSION_REQUIRED_ERROR = {
  code: "SESSION_REQUIRED",
  message: "A valid X-Session-Id header is required."
};
const IDEMPOTENCY_KEY_REQUIRED_ERROR = {
  code: "IDEMPOTENCY_KEY_REQUIRED",
  message: "A valid X-Idempotency-Key header is required."
};
const IDEMPOTENCY_KEY_CONFLICT_ERROR = {
  code: "IDEMPOTENCY_KEY_CONFLICT",
  message: "X-Idempotency-Key was already used with a different payload."
};
const IDEMPOTENCY_KEY_IN_PROGRESS_ERROR = {
  code: "IDEMPOTENCY_KEY_IN_PROGRESS",
  message: "A request with this X-Idempotency-Key is still being processed."
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
const VERSION_NOT_FOUND_ERROR = {
  code: "VERSION_NOT_FOUND",
  message: "Project version not found."
};
const INVALID_PREVIEW_QUERY_ERROR = {
  code: "INVALID_PREVIEW_QUERY",
  message: "Invalid preview query. Use target=current|version and versionNumber for target=version."
};
const PREVIEW_NOT_READY_ERROR = {
  code: "PREVIEW_NOT_READY",
  message: "Preview is not ready yet."
};
const PREVIEW_EXPIRED_ERROR = {
  code: "PREVIEW_EXPIRED",
  message: "Preview URL has expired."
};
const PREVIEW_UNAVAILABLE_ERROR = {
  code: "PREVIEW_UNAVAILABLE",
  message: "Preview is unavailable for this version."
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

function getValidIdempotencyKey(request, response) {
  const idempotencyKey = request.headers[IDEMPOTENCY_KEY_HEADER];
  if (typeof idempotencyKey !== "string" || idempotencyKey.trim().length === 0) {
    sendJson(response, 400, { error: IDEMPOTENCY_KEY_REQUIRED_ERROR });
    return null;
  }

  return idempotencyKey.trim();
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

function stableStringify(value) {
  if (Array.isArray(value)) {
    return `[${value.map((item) => stableStringify(item)).join(",")}]`;
  }

  if (value && typeof value === "object") {
    const keys = Object.keys(value).sort();
    return `{${keys.map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`).join(",")}}`;
  }

  return JSON.stringify(value);
}

function hashPayload(payload) {
  return createHash("sha256").update(stableStringify(payload)).digest("hex");
}

function tryParseJson(value) {
  if (typeof value !== "string") return null;
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

function createIdempotencyStore(db) {
  const findRequestStatement = db.prepare(`
    SELECT payload_hash, response_status, response_body
    FROM idempotency_requests
    WHERE session_id = ? AND endpoint = ? AND idempotency_key = ?;
  `);
  const insertRequestStatement = db.prepare(`
    INSERT INTO idempotency_requests (
      session_id,
      endpoint,
      idempotency_key,
      payload_hash,
      response_status,
      response_body,
      created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?);
  `);
  const updateResponseStatement = db.prepare(`
    UPDATE idempotency_requests
    SET response_status = ?, response_body = ?
    WHERE session_id = ? AND endpoint = ? AND idempotency_key = ?;
  `);

  return {
    claim({ sessionId, endpoint, idempotencyKey, payloadHash }) {
      db.exec("BEGIN IMMEDIATE;");
      try {
        const existing = findRequestStatement.get(sessionId, endpoint, idempotencyKey);
        if (existing) {
          db.exec("COMMIT;");
          if (existing.payload_hash !== payloadHash) {
            return { type: "conflict" };
          }

          if (typeof existing.response_status === "number" && typeof existing.response_body === "string") {
            const replayBody = tryParseJson(existing.response_body);
            if (replayBody === null) {
              return { type: "error" };
            }
            return {
              type: "replay",
              statusCode: existing.response_status,
              body: replayBody
            };
          }

          return { type: "in_progress" };
        }

        insertRequestStatement.run(
          sessionId,
          endpoint,
          idempotencyKey,
          payloadHash,
          null,
          null,
          new Date().toISOString()
        );
        db.exec("COMMIT;");
        return { type: "new" };
      } catch (error) {
        try {
          db.exec("ROLLBACK;");
        } catch {
          // noop: rollback can fail if transaction did not start.
        }
        throw error;
      }
    },
    saveResponse({ sessionId, endpoint, idempotencyKey, statusCode, body }) {
      updateResponseStatement.run(
        statusCode,
        JSON.stringify(body),
        sessionId,
        endpoint,
        idempotencyKey
      );
    }
  };
}

function createTelemetryStore(db) {
  const insertTelemetryEventStatement = db.prepare(`
    INSERT INTO telemetry_events (
      id,
      event_name,
      session_id,
      project_id,
      version_id,
      metadata,
      created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?);
  `);
  const countEventsByNameStatement = db.prepare(`
    SELECT event_name, COUNT(*) AS total
    FROM telemetry_events
    WHERE session_id = ?
    GROUP BY event_name;
  `);
  const listRecentEventsStatement = db.prepare(`
    SELECT event_name, project_id, version_id, metadata, created_at
    FROM telemetry_events
    WHERE session_id = ?
    ORDER BY created_at DESC, id DESC
    LIMIT ?;
  `);

  function track({ eventName, sessionId = null, projectId = null, versionId = null, metadata = null }) {
    const telemetryEvent = {
      id: randomUUID(),
      eventName,
      sessionId,
      projectId,
      versionId,
      metadata: sanitizeTelemetryMetadata(metadata),
      createdAt: new Date().toISOString()
    };

    insertTelemetryEventStatement.run(
      telemetryEvent.id,
      telemetryEvent.eventName,
      telemetryEvent.sessionId,
      telemetryEvent.projectId,
      telemetryEvent.versionId,
      JSON.stringify(telemetryEvent.metadata),
      telemetryEvent.createdAt
    );

    console.info(
      JSON.stringify({
        type: "telemetry",
        eventName: telemetryEvent.eventName,
        projectId: telemetryEvent.projectId,
        versionId: telemetryEvent.versionId,
        sessionId: telemetryEvent.sessionId,
        metadata: telemetryEvent.metadata,
        createdAt: telemetryEvent.createdAt
      })
    );
  }

  function getSummary({ sessionId, recentLimit = 25 }) {
    const counts = { create: 0, generate: 0, fail: 0, preview: 0, iterate: 0 };
    const countRows = countEventsByNameStatement.all(sessionId);
    for (const row of countRows) {
      if (Object.hasOwn(counts, row.event_name)) {
        counts[row.event_name] = row.total;
      }
    }

    const recentEvents = listRecentEventsStatement.all(sessionId, recentLimit).map((row) => ({
      eventName: row.event_name,
      projectId: row.project_id,
      versionId: row.version_id,
      createdAt: row.created_at,
      metadata: parseTelemetryMetadata(row.metadata)
    }));

    return {
      generatedAt: new Date().toISOString(),
      sessionId,
      totals: counts,
      recentEvents
    };
  }

  return { track, getSummary };
}

function normalizeDescription(value) {
  if (typeof value !== "string") {
    return null;
  }

  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
}

function sanitizeTelemetryMetadata(metadata) {
  if (!metadata || typeof metadata !== "object" || Array.isArray(metadata)) {
    return {};
  }
  const output = {};
  for (const [key, value] of Object.entries(metadata)) {
    if (value === null) {
      output[key] = null;
      continue;
    }
    if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
      output[key] = value;
    }
  }
  return output;
}

function parseTelemetryMetadata(metadata) {
  if (typeof metadata !== "string") return {};
  try {
    const parsed = JSON.parse(metadata);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

function safelyTrackTelemetry(telemetryStore, telemetryPayload) {
  try {
    telemetryStore.track(telemetryPayload);
  } catch (error) {
    console.error(
      JSON.stringify({
        type: "telemetry_error",
        eventName: telemetryPayload.eventName,
        projectId: telemetryPayload.projectId ?? null,
        versionId: telemetryPayload.versionId ?? null,
        message: error instanceof Error ? error.message : "unknown_telemetry_error"
      })
    );
  }
}

function createProjectHandler(db, idempotencyStore, telemetryStore) {
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

  return async function handleCreateProject(request, response, endpoint) {
    const sessionId = getValidSessionId(request, response);
    if (!sessionId) return;
    const idempotencyKey = getValidIdempotencyKey(request, response);
    if (!idempotencyKey) return;

    let body;
    try {
      body = await parseJsonBody(request);
    } catch {
      sendJson(response, 400, { error: INVALID_JSON_ERROR });
      return;
    }

    let idempotencyRecord;
    try {
      idempotencyRecord = idempotencyStore.claim({
        sessionId,
        endpoint,
        idempotencyKey,
        payloadHash: hashPayload(body)
      });
    } catch {
      sendJson(response, 500, {
        error: {
          code: "INTERNAL_ERROR",
          message: "Could not process idempotency key."
        }
      });
      return;
    }

    if (idempotencyRecord.type === "conflict") {
      sendJson(response, 409, { error: IDEMPOTENCY_KEY_CONFLICT_ERROR });
      return;
    }
    if (idempotencyRecord.type === "replay") {
      sendJson(response, idempotencyRecord.statusCode, idempotencyRecord.body);
      return;
    }
    if (idempotencyRecord.type === "in_progress") {
      sendJson(response, 409, { error: IDEMPOTENCY_KEY_IN_PROGRESS_ERROR });
      return;
    }
    if (idempotencyRecord.type === "error") {
      sendJson(response, 500, {
        error: {
          code: "INTERNAL_ERROR",
          message: "Could not replay idempotent response."
        }
      });
      return;
    }

    const sendIdempotentResponse = (statusCode, payload) => {
      idempotencyStore.saveResponse({
        sessionId,
        endpoint,
        idempotencyKey,
        statusCode,
        body: payload
      });
      sendJson(response, statusCode, payload);
    };

    const rawTitle = typeof body.title === "string" ? body.title.trim() : "";
    if (!rawTitle) {
      sendIdempotentResponse(400, { error: INVALID_TITLE_ERROR });
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

    safelyTrackTelemetry(telemetryStore, {
      eventName: "create",
      sessionId,
      projectId,
      metadata: { endpoint }
    });

    sendIdempotentResponse(201, { projectId });
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

function createCreatePromptHandler(db, idempotencyStore, telemetryStore) {
  const findProjectBySessionStatement = db.prepare(`
    SELECT id, current_version_id
    FROM projects
    WHERE id = ? AND session_id = ?;
  `);
  const findCurrentVersionMetaStatement = db.prepare(`
    SELECT provider_meta
    FROM project_versions
    WHERE id = ? AND project_id = ?;
  `);
  const getCurrentVersionNumberStatement = db.prepare(`
    SELECT COALESCE(MAX(version_number), 0) AS current_version_number
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
      preview_url,
      provider_meta,
      created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
  `);
  const updateProjectCurrentVersionStatement = db.prepare(`
    UPDATE projects
    SET current_version_id = ?, updated_at = ?
    WHERE id = ?
      AND EXISTS (
        SELECT 1
        FROM project_versions
        WHERE id = ?
          AND project_id = ?
          AND status = 'success'
      );
  `);

  return async function handleCreatePrompt(request, response, projectId, generationService, endpoint) {
    const sessionId = getValidSessionId(request, response);
    if (!sessionId) return;
    const idempotencyKey = getValidIdempotencyKey(request, response);
    if (!idempotencyKey) return;

    let body;
    try {
      body = await parseJsonBody(request);
    } catch {
      sendJson(response, 400, { error: INVALID_JSON_ERROR });
      return;
    }

    let idempotencyRecord;
    try {
      idempotencyRecord = idempotencyStore.claim({
        sessionId,
        endpoint,
        idempotencyKey,
        payloadHash: hashPayload(body)
      });
    } catch {
      sendJson(response, 500, {
        error: {
          code: "INTERNAL_ERROR",
          message: "Could not process idempotency key."
        }
      });
      return;
    }

    if (idempotencyRecord.type === "conflict") {
      sendJson(response, 409, { error: IDEMPOTENCY_KEY_CONFLICT_ERROR });
      return;
    }
    if (idempotencyRecord.type === "replay") {
      sendJson(response, idempotencyRecord.statusCode, idempotencyRecord.body);
      return;
    }
    if (idempotencyRecord.type === "in_progress") {
      sendJson(response, 409, { error: IDEMPOTENCY_KEY_IN_PROGRESS_ERROR });
      return;
    }
    if (idempotencyRecord.type === "error") {
      sendJson(response, 500, {
        error: {
          code: "INTERNAL_ERROR",
          message: "Could not replay idempotent response."
        }
      });
      return;
    }

    const sendIdempotentResponse = (statusCode, payload) => {
      idempotencyStore.saveResponse({
        sessionId,
        endpoint,
        idempotencyKey,
        statusCode,
        body: payload
      });
      sendJson(response, statusCode, payload);
    };

    const project = findProjectBySessionStatement.get(projectId, sessionId);
    if (!project) {
      sendIdempotentResponse(404, { error: PROJECT_NOT_FOUND_ERROR });
      return;
    }

    const prompt = typeof body.prompt === "string" ? body.prompt.trim() : "";
    if (!prompt) {
      sendIdempotentResponse(400, { error: INVALID_PROMPT_ERROR });
      return;
    }

    const promptMessageId = randomUUID();
    const projectVersionId = randomUUID();
    const now = new Date().toISOString();
    let status = "success";
    let providerMeta = null;
    let previewUrl = null;
    const currentVersionMeta = project.current_version_id
      ? findCurrentVersionMetaStatement.get(project.current_version_id, projectId)
      : null;
    const existingV0ChatId = extractExistingV0ChatId(currentVersionMeta?.provider_meta);

    try {
      const generationResult = await generateWithTimeout({
        provider: generationService.provider,
        payload: {
          projectId,
          prompt,
          sessionId,
          chatId: generationService.provider.name === "v0" ? existingV0ChatId : null
        },
        timeoutMs: generationService.timeoutMs
      });

      providerMeta = normalizeProviderMeta(generationResult.providerMeta, generationService.provider.name);
      previewUrl = extractPreviewUrl(providerMeta);
    } catch (error) {
      status = "failed";
      providerMeta = mapProviderErrorMeta(error, generationService.provider.name);
    }

    let versionNumber;
    try {
      db.exec("BEGIN IMMEDIATE;");
      const currentVersionRow = getCurrentVersionNumberStatement.get(projectId);
      versionNumber = currentVersionRow.current_version_number + 1;
      insertProjectVersionStatement.run(
        projectVersionId,
        projectId,
        versionNumber,
        prompt,
        status,
        previewUrl,
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
        updateProjectCurrentVersionStatement.run(
          projectVersionId,
          now,
          projectId,
          projectVersionId,
          projectId
        );
      }
      db.exec("COMMIT;");
    } catch {
      try {
        db.exec("ROLLBACK;");
      } catch {
        // noop: rollback can fail if transaction did not start.
      }
      sendIdempotentResponse(500, {
        error: {
          code: "INTERNAL_ERROR",
          message: "Could not create prompt and version."
        }
      });
      return;
    }

    safelyTrackTelemetry(telemetryStore, {
      eventName: "iterate",
      sessionId,
      projectId,
      versionId: projectVersionId,
      metadata: { versionNumber, status }
    });
    safelyTrackTelemetry(telemetryStore, {
      eventName: status === "success" ? "generate" : "fail",
      sessionId,
      projectId,
      versionId: projectVersionId,
      metadata: {
        versionNumber,
        status,
        errorCode: providerMeta?.errorCode ?? null
      }
    });

    sendIdempotentResponse(201, {
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
      version_number,
      prompt_snapshot,
      status,
      created_at
    FROM project_versions
    WHERE project_id = ?
    ORDER BY version_number DESC, created_at DESC, id DESC
    LIMIT 20;
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
      versionNumber: row.version_number,
      promptSnapshot: row.prompt_snapshot,
      status: row.status,
      createdAt: row.created_at
    }));
    sendJson(response, 200, versions);
  };
}

function createGetProjectPreviewHandler(db, telemetryStore) {
  const findProjectBySessionStatement = db.prepare(`
    SELECT id, current_version_id
    FROM projects
    WHERE id = ? AND session_id = ?;
  `);
  const findVersionByIdStatement = db.prepare(`
    SELECT
      id,
      version_number,
      status,
      preview_url,
      provider_meta
    FROM project_versions
    WHERE id = ? AND project_id = ?;
  `);
  const findVersionByNumberStatement = db.prepare(`
    SELECT
      id,
      version_number,
      status,
      preview_url,
      provider_meta
    FROM project_versions
    WHERE project_id = ? AND version_number = ?;
  `);

  return function handleGetProjectPreview(request, response, projectId, requestUrl) {
    const sessionId = getValidSessionId(request, response);
    if (!sessionId) return;

    const project = findProjectBySessionStatement.get(projectId, sessionId);
    if (!project) {
      sendJson(response, 404, { error: PROJECT_NOT_FOUND_ERROR });
      return;
    }

    const target = requestUrl.searchParams.get("target") ?? "current";
    if (target !== "current" && target !== "version") {
      sendJson(response, 400, { error: INVALID_PREVIEW_QUERY_ERROR });
      return;
    }

    let version;
    if (target === "current") {
      if (!project.current_version_id) {
        sendJson(response, 409, { error: PREVIEW_NOT_READY_ERROR });
        return;
      }

      version = findVersionByIdStatement.get(project.current_version_id, projectId);
      if (!version) {
        sendJson(response, 409, { error: PREVIEW_NOT_READY_ERROR });
        return;
      }
    } else {
      const rawVersionNumber = requestUrl.searchParams.get("versionNumber");
      const versionNumber = Number.parseInt(rawVersionNumber ?? "", 10);
      if (!Number.isInteger(versionNumber) || versionNumber <= 0) {
        sendJson(response, 400, { error: INVALID_PREVIEW_QUERY_ERROR });
        return;
      }

      version = findVersionByNumberStatement.get(projectId, versionNumber);
      if (!version) {
        sendJson(response, 404, { error: VERSION_NOT_FOUND_ERROR });
        return;
      }
    }

    if (isPreviewExpired(version)) {
      sendJson(response, 410, { error: PREVIEW_EXPIRED_ERROR });
      return;
    }

    const previewUrl = extractPreviewUrl(version);
    if (!previewUrl) {
      if (version.status !== "success") {
        sendJson(response, 409, { error: PREVIEW_NOT_READY_ERROR });
        return;
      }

      sendJson(response, 424, { error: PREVIEW_UNAVAILABLE_ERROR });
      return;
    }

    safelyTrackTelemetry(telemetryStore, {
      eventName: "preview",
      sessionId,
      projectId,
      versionId: version.id,
      metadata: {
        target,
        versionNumber: version.version_number
      }
    });

    sendJson(response, 200, {
      projectId,
      target,
      versionId: version.id,
      versionNumber: version.version_number,
      previewUrl
    });
  };
}

function createGetTelemetrySummaryHandler(telemetryStore) {
  return function handleGetTelemetrySummary(request, response) {
    const sessionId = getValidSessionId(request, response);
    if (!sessionId) return;

    const summary = telemetryStore.getSummary({ sessionId });
    sendJson(response, 200, summary);
  };
}

export function createApp({ db, generationProvider, generationTimeoutMs = DEFAULT_GENERATION_TIMEOUT_MS }) {
  const idempotencyStore = createIdempotencyStore(db);
  const telemetryStore = createTelemetryStore(db);
  const handleCreateProject = createProjectHandler(db, idempotencyStore, telemetryStore);
  const handleListProjects = createListProjectsHandler(db);
  const handleCreatePrompt = createCreatePromptHandler(db, idempotencyStore, telemetryStore);
  const handleListProjectMessages = createListProjectMessagesHandler(db);
  const handleListProjectVersions = createListProjectVersionsHandler(db);
  const handleGetProjectPreview = createGetProjectPreviewHandler(db, telemetryStore);
  const handleGetTelemetrySummary = createGetTelemetrySummaryHandler(telemetryStore);
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
    const projectPreviewMatch = /^\/projects\/([^/]+)\/preview$/.exec(path);

    if (request.method === "POST" && path === "/projects") {
      await handleCreateProject(request, response, path);
      return;
    }

    if (request.method === "GET" && path === "/projects") {
      handleListProjects(request, response);
      return;
    }

    if (request.method === "GET" && path === "/telemetry/summary") {
      handleGetTelemetrySummary(request, response);
      return;
    }

    if (request.method === "POST" && createPromptMatch) {
      const projectId = createPromptMatch[1];
      await handleCreatePrompt(request, response, projectId, generationService, path);
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

    if (request.method === "GET" && projectPreviewMatch) {
      const projectId = projectPreviewMatch[1];
      handleGetProjectPreview(request, response, projectId, requestUrl);
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

function parseProviderMeta(providerMeta) {
  if (!providerMeta) return null;
  if (typeof providerMeta === "object") return providerMeta;
  if (typeof providerMeta !== "string") return null;

  try {
    const parsed = JSON.parse(providerMeta);
    return parsed && typeof parsed === "object" ? parsed : null;
  } catch {
    return null;
  }
}

function extractPreviewUrl(source) {
  if (!source || typeof source !== "object") return null;
  if (typeof source.previewUrl === "string" && source.previewUrl.trim().length > 0) {
    return source.previewUrl.trim();
  }
  if (typeof source.preview_url === "string" && source.preview_url.trim().length > 0) {
    return source.preview_url.trim();
  }

  const providerMeta = parseProviderMeta(source.provider_meta ?? source.providerMeta);
  if (providerMeta && typeof providerMeta.previewUrl === "string") {
    const normalized = providerMeta.previewUrl.trim();
    return normalized.length > 0 ? normalized : null;
  }

  return null;
}

function extractExistingV0ChatId(providerMeta) {
  const parsedProviderMeta = parseProviderMeta(providerMeta);
  if (!parsedProviderMeta || parsedProviderMeta.provider !== "v0") return null;

  const chatId =
    typeof parsedProviderMeta.chatId === "string"
      ? parsedProviderMeta.chatId.trim()
      : typeof parsedProviderMeta.requestId === "string"
      ? parsedProviderMeta.requestId.trim()
      : "";

  return chatId.length > 0 ? chatId : null;
}

function isPreviewExpired(source) {
  const providerMeta = parseProviderMeta(source?.provider_meta ?? source?.providerMeta);
  if (!providerMeta) return false;

  if (providerMeta.errorCode === "PREVIEW_EXPIRED") return true;
  if (providerMeta.previewState === "expired") return true;
  if (providerMeta.previewStatus === "expired") return true;

  return false;
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
