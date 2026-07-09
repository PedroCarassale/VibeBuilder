import { createHash, randomUUID } from "node:crypto";
import { createServer } from "node:http";
import {
  AUTH_REQUIRED_ERROR,
  EMAIL_ALREADY_REGISTERED_ERROR,
  INVALID_AUTH_CREDENTIALS_ERROR,
  INVALID_LOGIN_BODY_ERROR,
  INVALID_REGISTER_BODY_ERROR,
  createAuthService
} from "./auth.js";
import { createArtifactService } from "./artifacts/artifact-service.js";
import { streamArtifactZip } from "./artifacts/zip-export.js";
import { resolveArtifactStorage } from "./artifacts/storage-factory.js";
import {
  ProviderTimeoutError,
  V0ProviderError,
  createMockV0Provider,
  createV0Provider
} from "./generation-provider.js";
import { testV0ApiConnection } from "./v0-connection-test.js";

const SESSION_ID_HEADER = "x-session-id";
const IDEMPOTENCY_KEY_HEADER = "x-idempotency-key";
const AUTHORIZATION_HEADER = "authorization";
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
  message: "title must be a string between 1 and 100 characters."
};
const INVALID_DESCRIPTION_ERROR = {
  code: "INVALID_DESCRIPTION",
  message: "description must be a string no longer than 500 characters."
};
const INVALID_PROJECT_UPDATE_ERROR = {
  code: "INVALID_PROJECT_UPDATE",
  message: "Body must contain only title and/or description."
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
const VERSION_NOT_REGENERABLE_ERROR = {
  code: "VERSION_NOT_REGENERABLE",
  message: "Only failed project versions can be regenerated."
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

const GENERATION_ASSISTANT_FALLBACK_MESSAGE =
  "Tu app ya está lista. Ábrela en el navegador para verla.";
const GENERATION_PROMPT_POLICY_ID = "mobile-first-no-db-no-third-party";
const GENERATION_PROMPT_GUARDRAILS = `
Reglas obligatorias para esta etapa de VibeBuilder:
- Crea una web app mobile-first y responsive. Prioriza pantallas angostas, navegación táctil y layouts que se vean bien desde un celular.
- No uses base de datos, ORM, servicios persistentes ni migraciones. Si necesitas datos, usa datos mock locales o estado en memoria/sesión del navegador.
- No integres proveedores externos ni intentes conectar APIs de terceros, autenticación real, pagos, email, analytics, storage remoto ni servicios cloud.
- Genera una app web completa y usable con dependencias mínimas, sin requerir configuración secreta ni infraestructura adicional.
- Mantén el alcance centrado en el pedido del usuario y evita reescribir todo si es una iteración.
`.trim();

const KEYSTORE_UNAVAILABLE_ERROR = {
  code: "KEYSTORE_UNAVAILABLE",
  message:
    "Server is not configured to store per-session v0 keys (set V0_KEYSTORE_SECRET, min 16 chars)."
};
const INVALID_V0_KEY_BODY_ERROR = {
  code: "INVALID_V0_KEY_BODY",
  message: "Body must be JSON with a non-empty string field \"apiKey\"."
};
const NO_STORED_V0_KEY_ERROR = {
  code: "NO_STORED_V0_KEY",
  message: "No saved v0 API key for this session. Pass \"apiKey\" in the body or save a key first."
};

function getValidSessionId(request, response) {
  const sessionId = request.headers[SESSION_ID_HEADER];
  if (typeof sessionId !== "string" || !UUID_V4_REGEX.test(sessionId)) {
    sendJson(response, 401, { error: SESSION_REQUIRED_ERROR });
    return null;
  }

  return sessionId;
}

function getBearerToken(request) {
  const authorization = request.headers[AUTHORIZATION_HEADER];
  if (typeof authorization !== "string") return null;
  const match = /^Bearer\s+(.+)$/i.exec(authorization.trim());
  return match ? match[1].trim() : "";
}

async function getRequestActor(request, response, authService) {
  const sessionId = getValidSessionId(request, response);
  if (!sessionId) return null;

  const bearerToken = getBearerToken(request);
  if (bearerToken !== null) {
    const authSession = await authService.resolveBearerToken(bearerToken);
    if (!authSession) {
      sendJson(response, 401, { error: AUTH_REQUIRED_ERROR });
      return null;
    }
    return {
      type: "user",
      sessionId,
      userId: authSession.user.id,
      user: authSession.user,
      authToken: bearerToken
    };
  }

  return {
    type: "guest",
    sessionId,
    userId: null,
    user: null,
    authToken: null
  };
}

function actorProjectWhere(alias = "") {
  const prefix = alias ? `${alias}.` : "";
  return `(
    (? IS NOT NULL AND ${prefix}user_id = ?)
    OR
    (? IS NULL AND ${prefix}user_id IS NULL AND ${prefix}session_id = ?)
  )`;
}

function actorProjectArgs(actor) {
  return [actor.userId, actor.userId, actor.userId, actor.sessionId];
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
  if (statusCode === 204) {
    response.writeHead(204);
    response.end();
    return;
  }
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
    async claim({ sessionId, endpoint, idempotencyKey, payloadHash }) {
      return db.transaction(async () => {
        const existing = await findRequestStatement.get(sessionId, endpoint, idempotencyKey);
        if (existing) {
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

        await insertRequestStatement.run(
          sessionId,
          endpoint,
          idempotencyKey,
          payloadHash,
          null,
          null,
          new Date().toISOString()
        );
        return { type: "new" };
      });
    },
    async saveResponse({ sessionId, endpoint, idempotencyKey, statusCode, body }) {
      await updateResponseStatement.run(
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

  async function track({ eventName, sessionId = null, projectId = null, versionId = null, metadata = null }) {
    const telemetryEvent = {
      id: randomUUID(),
      eventName,
      sessionId,
      projectId,
      versionId,
      metadata: sanitizeTelemetryMetadata(metadata),
      createdAt: new Date().toISOString()
    };

    await insertTelemetryEventStatement.run(
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

  async function getSummary({ sessionId, recentLimit = 25 }) {
    const counts = { create: 0, generate: 0, fail: 0, preview: 0, iterate: 0 };
    const countRows = await countEventsByNameStatement.all(sessionId);
    for (const row of countRows) {
      if (Object.hasOwn(counts, row.event_name)) {
        counts[row.event_name] = row.total;
      }
    }

      const recentEvents = (await listRecentEventsStatement.all(sessionId, recentLimit)).map((row) => ({
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

function validateProjectFields(body, { partial = false } = {}) {
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    return { error: partial ? INVALID_PROJECT_UPDATE_ERROR : INVALID_TITLE_ERROR };
  }

  const supportedFields = new Set(["title", "description"]);
  const fields = Object.keys(body);
  if (partial && (fields.length === 0 || fields.some((field) => !supportedFields.has(field)))) {
    return { error: INVALID_PROJECT_UPDATE_ERROR };
  }

  const hasTitle = Object.hasOwn(body, "title");
  const hasDescription = Object.hasOwn(body, "description");
  if ((!partial || hasTitle) && typeof body.title !== "string") {
    return { error: INVALID_TITLE_ERROR };
  }

  const title = hasTitle ? body.title.trim() : undefined;
  if (hasTitle && (title.length < 1 || title.length > 100)) {
    return { error: INVALID_TITLE_ERROR };
  }
  const optionalNullDescription = !partial && body.description === null;
  if (hasDescription && !optionalNullDescription &&
      (typeof body.description !== "string" || body.description.trim().length > 500)) {
    return { error: INVALID_DESCRIPTION_ERROR };
  }

  return {
    value: {
      hasTitle,
      title,
      hasDescription,
      description: hasDescription ? normalizeDescription(body.description) : undefined
    }
  };
}

function mapProject(row) {
  return {
    id: row.id,
    title: row.title,
    description: row.description,
    currentVersionId: row.current_version_id,
    createdAt: row.created_at,
    updatedAt: row.updated_at
  };
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
    void telemetryStore.track(telemetryPayload);
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

function buildGuardedGenerationPrompt(userPrompt) {
  return [
    GENERATION_PROMPT_GUARDRAILS,
    "",
    "Prompt del usuario:",
    userPrompt
  ].join("\n");
}

function createProjectHandler(db, idempotencyStore, telemetryStore, authService) {
  const insertProjectStatement = db.prepare(`
    INSERT INTO projects (
      id,
      session_id,
      user_id,
      title,
      description,
      current_version_id,
      created_at,
      updated_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
  `);

  return async function handleCreateProject(request, response, endpoint) {
    const actor = await getRequestActor(request, response, authService);
    if (!actor) return;
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
      idempotencyRecord = await idempotencyStore.claim({
        sessionId: actor.sessionId,
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

    const sendIdempotentResponse = async (statusCode, payload) => {
      await idempotencyStore.saveResponse({
        sessionId: actor.sessionId,
        endpoint,
        idempotencyKey,
        statusCode,
        body: payload
      });
      sendJson(response, statusCode, payload);
    };

    const validation = validateProjectFields(body);
    if (validation.error) {
      await sendIdempotentResponse(400, { error: validation.error });
      return;
    }

    const projectId = randomUUID();
    const now = new Date().toISOString();
    await insertProjectStatement.run(
      projectId,
      actor.sessionId,
      actor.userId,
      validation.value.title,
      validation.value.hasDescription ? validation.value.description : null,
      null,
      now,
      now
    );

    safelyTrackTelemetry(telemetryStore, {
      eventName: "create",
      sessionId: actor.sessionId,
      projectId,
      metadata: { endpoint, actorType: actor.type }
    });

    await sendIdempotentResponse(201, { projectId });
  };
}

function createListProjectsHandler(db, authService) {
  const listProjectsStatement = db.prepare(`
    SELECT
      id,
      title,
      description,
      current_version_id,
      created_at,
      updated_at
    FROM projects
    WHERE deleted_at IS NULL
      AND ${actorProjectWhere()}
    ORDER BY updated_at DESC;
  `);

  return async function handleListProjects(request, response) {
    const actor = await getRequestActor(request, response, authService);
    if (!actor) return;

    const rows = await listProjectsStatement.all(...actorProjectArgs(actor));
    const projects = rows.map(mapProject);

    sendJson(response, 200, projects);
  };
}

function createUpdateProjectHandler(db, authService) {
  const updateProjectStatement = db.prepare(`
    UPDATE projects
    SET
      title = CASE WHEN ? = 1 THEN ? ELSE title END,
      description = CASE WHEN ? = 1 THEN ? ELSE description END,
      updated_at = ?
    WHERE id = ? AND deleted_at IS NULL
      AND ${actorProjectWhere()}
    RETURNING id, title, description, current_version_id, created_at, updated_at;
  `);

  return async function handleUpdateProject(request, response, projectId) {
    const actor = await getRequestActor(request, response, authService);
    if (!actor) return;

    let body;
    try {
      body = await parseJsonBody(request);
    } catch {
      sendJson(response, 400, { error: INVALID_JSON_ERROR });
      return;
    }

    const validation = validateProjectFields(body, { partial: true });
    if (validation.error) {
      sendJson(response, 400, { error: validation.error });
      return;
    }

    const fields = validation.value;
    const row = await updateProjectStatement.get(
      fields.hasTitle ? 1 : 0,
      fields.title ?? null,
      fields.hasDescription ? 1 : 0,
      fields.description ?? null,
      new Date().toISOString(),
      projectId,
      ...actorProjectArgs(actor)
    );
    if (!row) {
      sendJson(response, 404, { error: PROJECT_NOT_FOUND_ERROR });
      return;
    }
    sendJson(response, 200, mapProject(row));
  };
}

function createDeleteProjectHandler(db, authService) {
  const deleteProjectStatement = db.prepare(`
    UPDATE projects
    SET deleted_at = ?, updated_at = ?
    WHERE id = ? AND deleted_at IS NULL
      AND ${actorProjectWhere()}
    RETURNING id;
  `);

  return async function handleDeleteProject(request, response, projectId) {
    const actor = await getRequestActor(request, response, authService);
    if (!actor) return;
    const now = new Date().toISOString();
    const deleted = await deleteProjectStatement.get(now, now, projectId, ...actorProjectArgs(actor));
    if (!deleted) {
      sendJson(response, 404, { error: PROJECT_NOT_FOUND_ERROR });
      return;
    }
    sendJson(response, 204);
  };
}

function createGenerationAttemptExecutor(
  db,
  telemetryStore,
  resolveGenerationService,
  artifactService
) {
  const findProjectByActorStatement = db.prepare(`
    SELECT id, current_version_id
    FROM projects
    WHERE id = ? AND deleted_at IS NULL
      AND ${actorProjectWhere()};
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
      source_version_id,
      attempt_number,
      failure_code,
      started_at,
      completed_at,
      created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
  `);
  const updateProjectCurrentVersionStatement = db.prepare(`
    UPDATE projects
    SET current_version_id = ?, updated_at = ?
    WHERE id = ?
      AND deleted_at IS NULL
      AND EXISTS (
        SELECT 1
        FROM project_versions
        WHERE id = ?
          AND project_id = ?
          AND status = 'success'
      );
  `);

  return async function executeGenerationAttempt({
    actor,
    projectId,
    prompt,
    sourceVersionId = null,
    attemptNumber = 1
  }) {
    const project = await findProjectByActorStatement.get(projectId, ...actorProjectArgs(actor));
    if (!project) {
      return { type: "not_found" };
    }

    const generationService = await resolveGenerationService(actor);

    const promptMessageId = randomUUID();
    const assistantPromptMessageId = randomUUID();
    const projectVersionId = randomUUID();
    const startedAt = new Date().toISOString();
    let completedAt = startedAt;
    let status = "success";
    let providerMeta = null;
    let previewUrl = null;
    let generationResult = null;
    let preparedArtifact = null;
    const currentVersionMeta = project.current_version_id
      ? await findCurrentVersionMetaStatement.get(project.current_version_id, projectId)
      : null;
    const existingV0ChatId = extractExistingV0ChatId(currentVersionMeta?.provider_meta);
    const generationPrompt = buildGuardedGenerationPrompt(prompt);

    try {
      generationResult = await generationService.provider.generate({
        projectId,
        prompt: generationPrompt,
        sessionId: actor.sessionId,
        chatId: generationService.provider.name === "v0" ? existingV0ChatId : null
      });

      providerMeta = normalizeProviderMeta(generationResult.providerMeta, generationService.provider.name);
      providerMeta.promptPolicy = GENERATION_PROMPT_POLICY_ID;
      providerMeta.userPromptLength = prompt.length;
      providerMeta.enhancedPromptLength = generationPrompt.length;
      providerMeta.promptLength = prompt.length;
      previewUrl = extractPreviewUrl(providerMeta);

      if (generationResult.artifactSource) {
        try {
          preparedArtifact = await artifactService.prepareArtifactPayload({
            projectId,
            actor,
            artifactSource: generationResult.artifactSource
          });
          if (preparedArtifact?.artifactSource?.previewUrl) {
            previewUrl = preparedArtifact.artifactSource.previewUrl;
            providerMeta.previewUrl = previewUrl;
          }
        } catch (error) {
          status = "failed";
          providerMeta = mapArtifactErrorMeta(error, generationService.provider.name, providerMeta);
        }
      } else {
        status = "failed";
        providerMeta = {
          ...providerMeta,
          errorType: "artifact_error",
          errorCode: "ARTIFACT_SOURCE_MISSING",
          retryable: true
        };
      }
    } catch (error) {
      status = "failed";
      providerMeta = mapProviderErrorMeta(error, generationService.provider.name);
    } finally {
      completedAt = new Date().toISOString();
    }

    if (providerMeta && typeof providerMeta === "object") {
      providerMeta.promptPolicy ??= GENERATION_PROMPT_POLICY_ID;
      providerMeta.userPromptLength ??= prompt.length;
      providerMeta.enhancedPromptLength ??= generationPrompt.length;
      providerMeta.promptLength = prompt.length;
    }

    let versionNumber;
    try {
      await db.transaction(async (tx) => {
        const currentVersionRow = await getCurrentVersionNumberStatement.get(projectId);
        versionNumber = currentVersionRow.current_version_number + 1;
        await insertProjectVersionStatement.run(
          projectVersionId,
          projectId,
          versionNumber,
          prompt,
          status,
          previewUrl,
          JSON.stringify(providerMeta),
          sourceVersionId,
          attemptNumber,
          providerMeta?.errorCode ?? null,
          startedAt,
          completedAt,
          startedAt
        );

        if (status === "success" && preparedArtifact) {
          try {
            await artifactService.insertPreparedArtifact(tx, {
              versionId: projectVersionId,
              prepared: preparedArtifact
            });
          } catch (error) {
            status = "failed";
            providerMeta = mapArtifactErrorMeta(
              error,
              generationService.provider.name,
              providerMeta
            );
            await tx
              .prepare(
                `UPDATE project_versions
                 SET status = ?, provider_meta = ?, failure_code = ?, preview_url = ?, completed_at = ?
                 WHERE id = ? AND project_id = ?;`
              )
              .run(
                "failed",
                JSON.stringify(providerMeta),
                providerMeta?.errorCode ?? null,
                previewUrl,
                new Date().toISOString(),
                projectVersionId,
                projectId
              );
            await artifactService.cleanupPreparedArtifact(preparedArtifact);
            preparedArtifact = null;
          }
        }

        await insertPromptMessageStatement.run(
          promptMessageId,
          projectId,
          projectVersionId,
          "user",
          prompt,
          startedAt
        );
        if (status === "success") {
          const rawAssistant =
            generationResult && typeof generationResult.assistantText === "string"
              ? generationResult.assistantText.trim()
              : "";
          const assistantContent =
            rawAssistant.length > 0 ? rawAssistant : GENERATION_ASSISTANT_FALLBACK_MESSAGE;
          const assistantCreatedAt = new Date(new Date(startedAt).getTime() + 1).toISOString();
          await insertPromptMessageStatement.run(
            assistantPromptMessageId,
            projectId,
            projectVersionId,
            "assistant",
            assistantContent,
            assistantCreatedAt
          );
          await updateProjectCurrentVersionStatement.run(
            projectVersionId,
            completedAt,
            projectId,
            projectVersionId,
            projectId
          );
        }
      });
    } catch {
      if (preparedArtifact) {
        await artifactService.cleanupPreparedArtifact(preparedArtifact);
      }
      return { type: "error" };
    }

    safelyTrackTelemetry(telemetryStore, {
      eventName: "iterate",
      sessionId: actor.sessionId,
      projectId,
      versionId: projectVersionId,
      metadata: { versionNumber, status, actorType: actor.type }
    });
    safelyTrackTelemetry(telemetryStore, {
      eventName: status === "success" ? "generate" : "fail",
      sessionId: actor.sessionId,
      projectId,
      versionId: projectVersionId,
      metadata: {
        versionNumber,
        status,
        errorCode: providerMeta?.errorCode ?? null,
        actorType: actor.type
      }
    });

    return {
      type: "created",
      payload: {
        promptMessageId,
        projectVersionId,
        versionNumber,
        status,
        providerMeta,
        sourceVersionId,
        attemptNumber,
        failureCode: providerMeta?.errorCode ?? null
      }
    };
  };
}

function createCreatePromptHandler(
  idempotencyStore,
  executeGenerationAttempt,
  authService
) {
  return async function handleCreatePrompt(request, response, projectId, endpoint) {
    const actor = await getRequestActor(request, response, authService);
    if (!actor) return;
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
      idempotencyRecord = await idempotencyStore.claim({
        sessionId: actor.sessionId,
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

    const sendIdempotentResponse = async (statusCode, payload) => {
      await idempotencyStore.saveResponse({
        sessionId: actor.sessionId,
        endpoint,
        idempotencyKey,
        statusCode,
        body: payload
      });
      sendJson(response, statusCode, payload);
    };

    const prompt = typeof body.prompt === "string" ? body.prompt.trim() : "";
    if (!prompt) {
      await sendIdempotentResponse(400, { error: INVALID_PROMPT_ERROR });
      return;
    }

    const result = await executeGenerationAttempt({
      actor,
      projectId,
      prompt
    });
    if (result.type === "not_found") {
      await sendIdempotentResponse(404, { error: PROJECT_NOT_FOUND_ERROR });
      return;
    }
    if (result.type === "error") {
      await sendIdempotentResponse(500, {
        error: {
          code: "INTERNAL_ERROR",
          message: "Could not create prompt and version."
        }
      });
      return;
    }

    await sendIdempotentResponse(201, result.payload);
  };
}

function createRegenerateVersionHandler(
  db,
  idempotencyStore,
  executeGenerationAttempt,
  authService
) {
  const findSourceVersionStatement = db.prepare(`
    SELECT
      v.id,
      v.project_id,
      v.prompt_snapshot,
      v.status,
      v.attempt_number
    FROM project_versions v
    INNER JOIN projects p ON p.id = v.project_id
    WHERE p.id = ?
      AND v.id = ?
      AND p.deleted_at IS NULL
      AND ${actorProjectWhere("p")};
  `);

  return async function handleRegenerateVersion(request, response, projectId, versionId, endpoint) {
    const actor = await getRequestActor(request, response, authService);
    if (!actor) return;
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
      idempotencyRecord = await idempotencyStore.claim({
        sessionId: actor.sessionId,
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

    const sendIdempotentResponse = async (statusCode, payload) => {
      await idempotencyStore.saveResponse({
        sessionId: actor.sessionId,
        endpoint,
        idempotencyKey,
        statusCode,
        body: payload
      });
      sendJson(response, statusCode, payload);
    };

    if (!body || typeof body !== "object" || Array.isArray(body)) {
      await sendIdempotentResponse(400, { error: INVALID_PROMPT_ERROR });
      return;
    }

    const sourceVersion = await findSourceVersionStatement.get(
      projectId,
      versionId,
      ...actorProjectArgs(actor)
    );
    if (!sourceVersion) {
      await sendIdempotentResponse(404, { error: VERSION_NOT_FOUND_ERROR });
      return;
    }
    if (sourceVersion.status !== "failed") {
      await sendIdempotentResponse(409, { error: VERSION_NOT_REGENERABLE_ERROR });
      return;
    }

    const hasCorrectedPrompt = Object.hasOwn(body, "prompt");
    const prompt = hasCorrectedPrompt
      ? (typeof body.prompt === "string" ? body.prompt.trim() : "")
      : sourceVersion.prompt_snapshot;
    if (!prompt) {
      await sendIdempotentResponse(400, { error: INVALID_PROMPT_ERROR });
      return;
    }

    const result = await executeGenerationAttempt({
      actor,
      projectId,
      prompt,
      sourceVersionId: sourceVersion.id,
      attemptNumber: Number(sourceVersion.attempt_number ?? 1) + 1
    });
    if (result.type === "not_found") {
      await sendIdempotentResponse(404, { error: PROJECT_NOT_FOUND_ERROR });
      return;
    }
    if (result.type === "error") {
      await sendIdempotentResponse(500, {
        error: {
          code: "INTERNAL_ERROR",
          message: "Could not regenerate project version."
        }
      });
      return;
    }

    await sendIdempotentResponse(201, result.payload);
  };
}

function createListProjectMessagesHandler(db, authService) {
  const findProjectByActorStatement = db.prepare(`
    SELECT id
    FROM projects
    WHERE id = ? AND deleted_at IS NULL
      AND ${actorProjectWhere()};
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

  return async function handleListProjectMessages(request, response, projectId) {
    const actor = await getRequestActor(request, response, authService);
    if (!actor) return;

    const project = await findProjectByActorStatement.get(projectId, ...actorProjectArgs(actor));
    if (!project) {
      sendJson(response, 404, { error: PROJECT_NOT_FOUND_ERROR });
      return;
    }

    const rows = await listMessagesStatement.all(projectId);
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

function createListProjectVersionsHandler(db, artifactService, authService) {
  const findProjectByActorStatement = db.prepare(`
    SELECT id
    FROM projects
    WHERE id = ? AND deleted_at IS NULL
      AND ${actorProjectWhere()};
  `);
  const listVersionsStatement = db.prepare(`
    SELECT
      id,
      project_id,
      version_number,
      prompt_snapshot,
      status,
      preview_url,
      source_version_id,
      attempt_number,
      failure_code,
      started_at,
      completed_at,
      created_at
    FROM project_versions
    WHERE project_id = ?
    ORDER BY version_number DESC, created_at DESC, id DESC
    LIMIT 20;
  `);

  return async function handleListProjectVersions(request, response, projectId) {
    const actor = await getRequestActor(request, response, authService);
    if (!actor) return;

    const project = await findProjectByActorStatement.get(projectId, ...actorProjectArgs(actor));
    if (!project) {
      sendJson(response, 404, { error: PROJECT_NOT_FOUND_ERROR });
      return;
    }

    const rows = await listVersionsStatement.all(projectId);
    const artifactSummaries = await artifactService.getArtifactSummaryForVersions(projectId);
    const versions = rows.map((row) => ({
      id: row.id,
      projectId: row.project_id,
      versionNumber: row.version_number,
      promptSnapshot: row.prompt_snapshot,
      status: row.status,
      previewUrl: row.preview_url,
      sourceVersionId: row.source_version_id,
      attemptNumber: row.attempt_number,
      failureCode: row.failure_code,
      startedAt: row.started_at,
      completedAt: row.completed_at,
      createdAt: row.created_at,
      artifact: artifactSummaries.get(row.id)?.artifact ?? null
    }));
    sendJson(response, 200, versions);
  };
}

function createGetProjectVersionDetailHandler(db, artifactService, authService) {
  return async function handleGetProjectVersionDetail(request, response, projectId, versionNumber) {
    const actor = await getRequestActor(request, response, authService);
    if (!actor) return;

    const parsedVersionNumber = Number.parseInt(versionNumber, 10);
    if (!Number.isFinite(parsedVersionNumber) || parsedVersionNumber <= 0) {
      sendJson(response, 404, { error: PROJECT_NOT_FOUND_ERROR });
      return;
    }

    const versionRow = await db
      .prepare(
        `SELECT
           id,
           project_id,
           version_number,
           prompt_snapshot,
           status,
           created_at,
           preview_url,
           source_version_id,
           attempt_number,
           failure_code,
           started_at,
           completed_at
         FROM project_versions
         WHERE project_id = ? AND version_number = ?;`
      )
      .get(projectId, parsedVersionNumber);

    const artifact = await artifactService.getVersionArtifactDetail({
      projectId,
      versionNumber: parsedVersionNumber,
      actor
    });

    if (!versionRow || !artifact) {
      sendJson(response, 404, { error: PROJECT_NOT_FOUND_ERROR });
      return;
    }

    sendJson(response, 200, {
      id: versionRow.id,
      projectId: versionRow.project_id,
      versionNumber: versionRow.version_number,
      promptSnapshot: versionRow.prompt_snapshot,
      status: versionRow.status,
      createdAt: versionRow.created_at,
      previewUrl: versionRow.preview_url,
      sourceVersionId: versionRow.source_version_id,
      attemptNumber: versionRow.attempt_number,
      failureCode: versionRow.failure_code,
      startedAt: versionRow.started_at,
      completedAt: versionRow.completed_at,
      artifact
    });
  };
}

function createExportProjectVersionHandler(artifactService, authService) {
  return async function handleExportProjectVersion(request, response, projectId, versionNumber) {
    const actor = await getRequestActor(request, response, authService);
    if (!actor) return;

    const parsedVersionNumber = Number.parseInt(versionNumber, 10);
    if (!Number.isFinite(parsedVersionNumber) || parsedVersionNumber <= 0) {
      sendJson(response, 404, { error: PROJECT_NOT_FOUND_ERROR });
      return;
    }

    const exportPayload = await artifactService.openVersionExport({
      projectId,
      versionNumber: parsedVersionNumber,
      actor
    });

    if (!exportPayload) {
      sendJson(response, 404, { error: PROJECT_NOT_FOUND_ERROR });
      return;
    }

    await streamArtifactZip({
      response,
      files: exportPayload.files,
      artifactStorage: artifactService.artifactStorage,
      archiveName: `project-${projectId}-v${parsedVersionNumber}.zip`
    });
  };
}

function createGetProjectPreviewHandler(db, telemetryStore, authService) {
  const findProjectByActorStatement = db.prepare(`
    SELECT id, current_version_id
    FROM projects
    WHERE id = ? AND deleted_at IS NULL
      AND ${actorProjectWhere()};
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

  return async function handleGetProjectPreview(request, response, projectId, requestUrl) {
    const actor = await getRequestActor(request, response, authService);
    if (!actor) return;

    const project = await findProjectByActorStatement.get(projectId, ...actorProjectArgs(actor));
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

      version = await findVersionByIdStatement.get(project.current_version_id, projectId);
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

      version = await findVersionByNumberStatement.get(projectId, versionNumber);
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
      sessionId: actor.sessionId,
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

function createGenerationServiceResolver({
  sessionV0KeyStore,
  userV0KeyStore,
  generationProvider,
  v0ApiBaseUrl,
  sessionGenerationProviderFactory
}) {
  const defaultProvider = generationProvider ?? createMockV0Provider();

  return async function resolveGenerationService(actor) {
    const userKey =
      actor?.userId && userV0KeyStore?.isEnabled
        ? await userV0KeyStore.getDecryptedApiKey(actor.userId)
        : null;
    const sessionKey =
      sessionV0KeyStore?.isEnabled
        ? await sessionV0KeyStore.getDecryptedApiKey(actor.sessionId)
        : null;
    const plain = userKey || sessionKey;
    if (typeof plain === "string" && plain.trim().length > 0) {
      const trimmed = plain.trim();
      const baseUrl =
        typeof v0ApiBaseUrl === "string" && v0ApiBaseUrl.trim().length > 0
          ? v0ApiBaseUrl.trim()
          : undefined;
      const provider =
        typeof sessionGenerationProviderFactory === "function"
          ? sessionGenerationProviderFactory(trimmed, baseUrl)
          : createV0Provider(
              baseUrl ? { apiKey: trimmed, baseUrl } : { apiKey: trimmed }
            );
      return { provider };
    }

    return { provider: defaultProvider };
  };
}

function createV0IntegrationHandlers({
  sessionV0KeyStore,
  userV0KeyStore,
  defaultGenerationProvider,
  v0ApiBaseUrl,
  authService
}) {
  function selectV0KeyStore(actor) {
    if (actor.type === "user") {
      return {
        store: userV0KeyStore,
        ownerId: actor.userId,
        ownerType: "user"
      };
    }
    return {
      store: sessionV0KeyStore,
      ownerId: actor.sessionId,
      ownerType: "session"
    };
  }

  async function handleGetV0Integration(request, response) {
    const actor = await getRequestActor(request, response, authService);
    if (!actor) return;

    const selected = selectV0KeyStore(actor);
    const storageAvailable = Boolean(selected.store?.isEnabled);
    const sessionStatus = storageAvailable
      ? await selected.store.getStatus(selected.ownerId)
      : { configured: false, keyHint: null };

    sendJson(response, 200, {
      keyStorageAvailable: storageAvailable,
      sessionKeyConfigured: sessionStatus.configured,
      sessionKeyHint: sessionStatus.keyHint,
      envKeyActive: defaultGenerationProvider?.name === "v0",
      ownerType: selected.ownerType
    });
  }

  async function handlePutV0Integration(request, response) {
    const actor = await getRequestActor(request, response, authService);
    if (!actor) return;
    const selected = selectV0KeyStore(actor);

    if (!selected.store?.isEnabled) {
      sendJson(response, 503, { error: KEYSTORE_UNAVAILABLE_ERROR });
      return;
    }

    let body;
    try {
      body = await parseJsonBody(request);
    } catch {
      sendJson(response, 400, { error: INVALID_JSON_ERROR });
      return;
    }

    const apiKey = typeof body.apiKey === "string" ? body.apiKey : "";
    if (!apiKey.trim()) {
      sendJson(response, 400, { error: INVALID_V0_KEY_BODY_ERROR });
      return;
    }

    try {
      await selected.store.save(selected.ownerId, apiKey);
    } catch (error) {
      sendJson(response, 400, {
        error: {
          code: "INVALID_V0_KEY",
          message: error?.message ?? "Could not save API key."
        }
      });
      return;
    }

    sendJson(response, 204);
  }

  async function handleDeleteV0Integration(request, response) {
    const actor = await getRequestActor(request, response, authService);
    if (!actor) return;
    const selected = selectV0KeyStore(actor);

    if (!selected.store?.isEnabled) {
      sendJson(response, 503, { error: KEYSTORE_UNAVAILABLE_ERROR });
      return;
    }

    await selected.store.delete(selected.ownerId);
    sendJson(response, 204);
  }

  async function handlePostV0IntegrationTest(request, response) {
    const actor = await getRequestActor(request, response, authService);
    if (!actor) return;
    const selected = selectV0KeyStore(actor);

    let body = {};
    try {
      body = await parseJsonBody(request);
    } catch {
      sendJson(response, 400, { error: INVALID_JSON_ERROR });
      return;
    }

    let candidate =
      typeof body.apiKey === "string" && body.apiKey.trim().length > 0 ? body.apiKey.trim() : null;

    if (!candidate) {
      if (!selected.store?.isEnabled) {
        sendJson(response, 503, { error: KEYSTORE_UNAVAILABLE_ERROR });
        return;
      }
      candidate = await selected.store.getDecryptedApiKey(selected.ownerId);
      if (!candidate?.trim()) {
        sendJson(response, 400, { error: NO_STORED_V0_KEY_ERROR });
        return;
      }
    }

    const baseUrl =
      typeof v0ApiBaseUrl === "string" && v0ApiBaseUrl.trim().length > 0
        ? v0ApiBaseUrl.trim()
        : undefined;

    try {
      await testV0ApiConnection({ apiKey: candidate, baseUrl });
    } catch (error) {
      sendJson(response, 400, {
        error: {
          code: "V0_CONNECTION_FAILED",
          message: error?.message ?? "v0 API connection test failed."
        }
      });
      return;
    }

    sendJson(response, 200, { ok: true });
  }

  return {
    handleGetV0Integration,
    handlePutV0Integration,
    handleDeleteV0Integration,
    handlePostV0IntegrationTest
  };
}

function createAuthHandlers(authService) {
  async function handlePostRegister(request, response) {
    const sessionId = getValidSessionId(request, response);
    if (!sessionId) return;

    let body;
    try {
      body = await parseJsonBody(request);
    } catch {
      sendJson(response, 400, { error: INVALID_JSON_ERROR });
      return;
    }

    const result = await authService.register({
      email: body.email,
      password: body.password,
      name: body.name
    });
    if (result.type === "invalid_body") {
      sendJson(response, 400, { error: INVALID_REGISTER_BODY_ERROR });
      return;
    }
    if (result.type === "email_exists") {
      sendJson(response, 409, { error: EMAIL_ALREADY_REGISTERED_ERROR });
      return;
    }

    sendJson(response, 201, {
      token: result.token,
      expiresAt: result.expiresAt,
      user: result.user
    });
  }

  async function handlePostLogin(request, response) {
    const sessionId = getValidSessionId(request, response);
    if (!sessionId) return;

    let body;
    try {
      body = await parseJsonBody(request);
    } catch {
      sendJson(response, 400, { error: INVALID_JSON_ERROR });
      return;
    }

    const result = await authService.login({
      email: body.email,
      password: body.password
    });
    if (result.type === "invalid_body") {
      sendJson(response, 400, { error: INVALID_LOGIN_BODY_ERROR });
      return;
    }
    if (result.type !== "signed_in") {
      sendJson(response, 401, { error: INVALID_AUTH_CREDENTIALS_ERROR });
      return;
    }

    sendJson(response, 200, {
      token: result.token,
      expiresAt: result.expiresAt,
      user: result.user
    });
  }

  async function handleGetMe(request, response) {
    const bearerToken = getBearerToken(request);
    if (bearerToken === null) {
      sendJson(response, 401, { error: AUTH_REQUIRED_ERROR });
      return;
    }
    const authSession = await authService.resolveBearerToken(bearerToken);
    if (!authSession) {
      sendJson(response, 401, { error: AUTH_REQUIRED_ERROR });
      return;
    }

    sendJson(response, 200, {
      user: authSession.user
    });
  }

  async function handlePostLogout(request, response) {
    const bearerToken = getBearerToken(request);
    if (bearerToken === null) {
      sendJson(response, 401, { error: AUTH_REQUIRED_ERROR });
      return;
    }
    const authSession = await authService.resolveBearerToken(bearerToken);
    if (!authSession) {
      sendJson(response, 401, { error: AUTH_REQUIRED_ERROR });
      return;
    }

    await authService.revokeBearerToken(bearerToken);
    sendJson(response, 204);
  }

  return {
    handlePostRegister,
    handlePostLogin,
    handleGetMe,
    handlePostLogout
  };
}

function createGetTelemetrySummaryHandler(telemetryStore) {
  return async function handleGetTelemetrySummary(request, response) {
    const sessionId = getValidSessionId(request, response);
    if (!sessionId) return;

    const summary = await telemetryStore.getSummary({ sessionId });
    sendJson(response, 200, summary);
  };
}

export function createApp({
  db,
  generationProvider,
  sessionV0KeyStore = null,
  userV0KeyStore = null,
  v0ApiBaseUrl,
  sessionGenerationProviderFactory = null,
  artifactStorage = null,
  authTokenTtlMs
} = {}) {
  const idempotencyStore = createIdempotencyStore(db);
  const telemetryStore = createTelemetryStore(db);
  const authService = createAuthService({
    db,
    tokenTtlMs: authTokenTtlMs
  });
  const resolvedArtifactStorage = artifactStorage ?? resolveArtifactStorage();
  const artifactService = createArtifactService({
    db,
    artifactStorage: resolvedArtifactStorage
  });
  const authHandlers = createAuthHandlers(authService);
  const handleCreateProject = createProjectHandler(db, idempotencyStore, telemetryStore, authService);
  const handleListProjects = createListProjectsHandler(db, authService);
  const handleUpdateProject = createUpdateProjectHandler(db, authService);
  const handleDeleteProject = createDeleteProjectHandler(db, authService);
  const defaultGenerationProvider = generationProvider ?? createMockV0Provider();
  const resolveGenerationService = createGenerationServiceResolver({
    sessionV0KeyStore,
    userV0KeyStore,
    generationProvider,
    v0ApiBaseUrl,
    sessionGenerationProviderFactory
  });
  const executeGenerationAttempt = createGenerationAttemptExecutor(
    db,
    telemetryStore,
    resolveGenerationService,
    artifactService
  );
  const handleCreatePrompt = createCreatePromptHandler(
    idempotencyStore,
    executeGenerationAttempt,
    authService
  );
  const handleRegenerateVersion = createRegenerateVersionHandler(
    db,
    idempotencyStore,
    executeGenerationAttempt,
    authService
  );
  const handleListProjectMessages = createListProjectMessagesHandler(db, authService);
  const handleListProjectVersions = createListProjectVersionsHandler(db, artifactService, authService);
  const handleGetProjectVersionDetail = createGetProjectVersionDetailHandler(db, artifactService, authService);
  const handleExportProjectVersion = createExportProjectVersionHandler(artifactService, authService);
  const handleGetProjectPreview = createGetProjectPreviewHandler(db, telemetryStore, authService);
  const handleGetTelemetrySummary = createGetTelemetrySummaryHandler(telemetryStore);
  const v0IntegrationHandlers = createV0IntegrationHandlers({
    sessionV0KeyStore,
    userV0KeyStore,
    defaultGenerationProvider,
    v0ApiBaseUrl,
    authService
  });

  return createServer(async (request, response) => {
    const requestUrl = new URL(request.url, "http://localhost");
    const path = requestUrl.pathname;
    const createPromptMatch = /^\/projects\/([^/]+)\/prompts$/.exec(path);
    const listMessagesMatch = /^\/projects\/([^/]+)\/messages$/.exec(path);
    const listVersionsMatch = /^\/projects\/([^/]+)\/versions$/.exec(path);
    const regenerateVersionMatch = /^\/projects\/([^/]+)\/versions\/([^/]+)\/regenerate$/.exec(path);
    const versionDetailMatch = /^\/projects\/([^/]+)\/versions\/(\d+)$/.exec(path);
    const versionExportMatch = /^\/projects\/([^/]+)\/versions\/(\d+)\/export$/.exec(path);
    const projectPreviewMatch = /^\/projects\/([^/]+)\/preview$/.exec(path);
    const projectMatch = /^\/projects\/([^/]+)$/.exec(path);

    if (request.method === "POST" && path === "/auth/register") {
      await authHandlers.handlePostRegister(request, response);
      return;
    }

    if (request.method === "POST" && path === "/auth/login") {
      await authHandlers.handlePostLogin(request, response);
      return;
    }

    if (request.method === "GET" && path === "/auth/me") {
      await authHandlers.handleGetMe(request, response);
      return;
    }

    if (request.method === "POST" && path === "/auth/logout") {
      await authHandlers.handlePostLogout(request, response);
      return;
    }

    if (request.method === "POST" && path === "/projects") {
      await handleCreateProject(request, response, path);
      return;
    }

    if (request.method === "GET" && path === "/projects") {
      await handleListProjects(request, response);
      return;
    }

    if (request.method === "PATCH" && projectMatch) {
      await handleUpdateProject(request, response, projectMatch[1]);
      return;
    }

    if (request.method === "DELETE" && projectMatch) {
      await handleDeleteProject(request, response, projectMatch[1]);
      return;
    }

    if (request.method === "GET" && path === "/telemetry/summary") {
      await handleGetTelemetrySummary(request, response);
      return;
    }

    if (request.method === "GET" && path === "/integrations/v0") {
      await v0IntegrationHandlers.handleGetV0Integration(request, response);
      return;
    }

    if (request.method === "PUT" && path === "/integrations/v0") {
      await v0IntegrationHandlers.handlePutV0Integration(request, response);
      return;
    }

    if (request.method === "DELETE" && path === "/integrations/v0") {
      await v0IntegrationHandlers.handleDeleteV0Integration(request, response);
      return;
    }

    if (request.method === "POST" && path === "/integrations/v0/test") {
      await v0IntegrationHandlers.handlePostV0IntegrationTest(request, response);
      return;
    }

    if (request.method === "POST" && createPromptMatch) {
      const projectId = createPromptMatch[1];
      await handleCreatePrompt(request, response, projectId, path);
      return;
    }

    if (request.method === "POST" && regenerateVersionMatch) {
      const projectId = regenerateVersionMatch[1];
      const versionId = regenerateVersionMatch[2];
      await handleRegenerateVersion(request, response, projectId, versionId, path);
      return;
    }

    if (request.method === "GET" && listMessagesMatch) {
      const projectId = listMessagesMatch[1];
      await handleListProjectMessages(request, response, projectId);
      return;
    }

    if (request.method === "GET" && listVersionsMatch) {
      const projectId = listVersionsMatch[1];
      await handleListProjectVersions(request, response, projectId);
      return;
    }

    if (request.method === "GET" && versionExportMatch) {
      const projectId = versionExportMatch[1];
      const versionNumber = versionExportMatch[2];
      await handleExportProjectVersion(request, response, projectId, versionNumber);
      return;
    }

    if (request.method === "GET" && versionDetailMatch) {
      const projectId = versionDetailMatch[1];
      const versionNumber = versionDetailMatch[2];
      await handleGetProjectVersionDetail(request, response, projectId, versionNumber);
      return;
    }

    if (request.method === "GET" && projectPreviewMatch) {
      const projectId = projectPreviewMatch[1];
      await handleGetProjectPreview(request, response, projectId, requestUrl);
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
  if (typeof providerMeta.chatId === "string") normalized.chatId = providerMeta.chatId;

  return normalized;
}

function mapArtifactErrorMeta(error, providerName, baseMeta = {}) {
  const code =
    typeof error?.code === "string" && error.code.length > 0
      ? error.code
      : "ARTIFACT_PERSISTENCE_FAILED";

  return {
    ...baseMeta,
    provider: providerName,
    errorType: "artifact_error",
    errorCode: code,
    retryable: true
  };
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
