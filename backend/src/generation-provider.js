const DEFAULT_PROVIDER_DELAY_MS = 25;
const HTTP_STATUS_FROM_MESSAGE_REGEX = /^HTTP\s+(\d{3})\b/i;

function isAbortError(error) {
  return Boolean(error) && typeof error === "object" && error.name === "AbortError";
}

function createAbortError(message = "Generation was aborted.") {
  const abortError = new Error(message);
  abortError.name = "AbortError";
  return abortError;
}

function waitWithAbort(ms, signal) {
  return new Promise((resolve, reject) => {
    const timeoutId = setTimeout(resolve, ms);
    const onAbort = () => {
      clearTimeout(timeoutId);
      reject(createAbortError());
    };

    if (signal?.aborted) {
      onAbort();
      return;
    }

    signal?.addEventListener("abort", onAbort, { once: true });
  });
}

function raceWithAbort(promise, signal) {
  if (!signal) {
    return promise;
  }

  if (signal.aborted) {
    return Promise.reject(createAbortError());
  }

  return new Promise((resolve, reject) => {
    let settled = false;
    const onAbort = () => {
      if (settled) return;
      settled = true;
      reject(createAbortError());
    };

    signal.addEventListener("abort", onAbort, { once: true });

    promise.then(
      (value) => {
        if (settled) return;
        settled = true;
        signal.removeEventListener("abort", onAbort);
        resolve(value);
      },
      (error) => {
        if (settled) return;
        settled = true;
        signal.removeEventListener("abort", onAbort);
        reject(error);
      }
    );
  });
}

export function createMockV0Provider({ defaultDelayMs = DEFAULT_PROVIDER_DELAY_MS } = {}) {
  return {
    name: "mock-v0",
    async generate({ prompt, signal }) {
      const startedAt = Date.now();
      await waitWithAbort(defaultDelayMs, signal);
      const elapsedMs = Date.now() - startedAt;

      return {
        providerMeta: {
          provider: "mock-v0",
          model: "v0-simulated",
          requestId: `mock-${Date.now()}`,
          latencyMs: elapsedMs,
          promptLength: prompt.length
        }
      };
    }
  };
}

export class ProviderTimeoutError extends Error {
  constructor(message = "Generation provider timed out.") {
    super(message);
    this.name = "ProviderTimeoutError";
  }
}

export class V0ProviderError extends Error {
  constructor({ message, status = null, code = null, retryable = false } = {}) {
    super(message ?? "v0 provider request failed.");
    this.name = "V0ProviderError";
    this.status = typeof status === "number" ? status : null;
    this.code = typeof code === "string" ? code : null;
    this.retryable = Boolean(retryable);
  }
}

function isRetryableStatus(status) {
  if (status === null) return true;
  if (status === 408 || status === 425 || status === 429) return true;
  if (status >= 500 && status <= 599) return true;
  return false;
}

function classifyV0Error(error) {
  if (error instanceof V0ProviderError) {
    return error;
  }

  if (isAbortError(error)) {
    return error;
  }

  const rawMessage = typeof error?.message === "string" ? error.message : "";
  const httpMatch = HTTP_STATUS_FROM_MESSAGE_REGEX.exec(rawMessage);
  const status =
    typeof error?.status === "number"
      ? error.status
      : httpMatch
      ? Number.parseInt(httpMatch[1], 10)
      : null;
  const code = typeof error?.code === "string" ? error.code : null;

  return new V0ProviderError({
    message: "v0 provider request failed.",
    status,
    code,
    retryable: isRetryableStatus(status)
  });
}

function pickStringField(source, fields) {
  if (!source || typeof source !== "object") return null;

  for (const field of fields) {
    const value = source[field];
    if (typeof value === "string" && value.trim().length > 0) {
      return value;
    }
  }

  return null;
}

function extractV0ProviderMeta(result, prompt, elapsedMs) {
  const meta = {
    provider: "v0",
    latencyMs: elapsedMs,
    promptLength: prompt.length
  };

  const requestId = pickStringField(result, ["id"]);
  if (requestId) {
    meta.requestId = requestId;
  }

  const modelId =
    pickStringField(result?.modelConfiguration, ["modelId"]) ||
    pickStringField(result?.latestVersion, ["modelId"]);
  if (modelId) {
    meta.model = modelId;
  }

  const versionStatus = pickStringField(result?.latestVersion, ["status"]);
  if (versionStatus) {
    meta.finishReason = versionStatus;
  }

  const previewUrl = pickStringField(result?.latestVersion, ["demoUrl"]);
  if (previewUrl) {
    meta.previewUrl = previewUrl;
  }

  return meta;
}

export function createV0Provider({
  apiKey,
  baseUrl,
  client = null,
  clientFactory = null,
  buildMessage = null
} = {}) {
  if (!client && !clientFactory && (typeof apiKey !== "string" || apiKey.trim().length === 0)) {
    throw new Error("V0_API_KEY is required to create the v0 provider.");
  }

  let cachedClient = client;

  async function ensureClient() {
    if (cachedClient) return cachedClient;

    if (typeof clientFactory === "function") {
      cachedClient = await clientFactory();
      return cachedClient;
    }

    const sdk = await import("v0-sdk");
    const factory = sdk.createClient ?? sdk.default?.createClient;
    if (typeof factory !== "function") {
      throw new Error("v0-sdk does not expose a createClient() factory.");
    }

    cachedClient = factory({
      apiKey,
      ...(typeof baseUrl === "string" && baseUrl.trim().length > 0 ? { baseUrl } : {})
    });
    return cachedClient;
  }

  return {
    name: "v0",
    async generate({ prompt, signal }) {
      let v0Client;
      try {
        v0Client = await ensureClient();
      } catch (error) {
        throw classifyV0Error(error);
      }

      if (!v0Client?.chats || typeof v0Client.chats.create !== "function") {
        throw new V0ProviderError({
          message: "v0 client is missing chats.create().",
          retryable: false
        });
      }

      const message =
        typeof buildMessage === "function" ? buildMessage(prompt) : prompt;

      const startedAt = Date.now();
      try {
        const result = await raceWithAbort(
          Promise.resolve().then(() => v0Client.chats.create({ message })),
          signal
        );
        const elapsedMs = Date.now() - startedAt;
        return {
          providerMeta: extractV0ProviderMeta(result, prompt, elapsedMs)
        };
      } catch (error) {
        if (isAbortError(error)) {
          throw error;
        }
        throw classifyV0Error(error);
      }
    }
  };
}

export async function generateWithTimeout({ provider, payload, timeoutMs }) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  try {
    return await provider.generate({ ...payload, signal: controller.signal });
  } catch (error) {
    if (isAbortError(error)) {
      throw new ProviderTimeoutError();
    }
    throw error;
  } finally {
    clearTimeout(timeoutId);
  }
}

export function resolveGenerationProvider({
  env = process.env,
  logger = console
} = {}) {
  const apiKey = typeof env.V0_API_KEY === "string" ? env.V0_API_KEY.trim() : "";

  if (!apiKey) {
    logger?.warn?.(
      "[generation-provider] V0_API_KEY is not set; falling back to mock provider."
    );
    return createMockV0Provider();
  }

  const baseUrl =
    typeof env.V0_API_URL === "string" && env.V0_API_URL.trim().length > 0
      ? env.V0_API_URL.trim()
      : undefined;

  return createV0Provider({ apiKey, baseUrl });
}

export function resolveGenerationTimeoutMs({
  env = process.env,
  fallbackMs
} = {}) {
  const raw = env.V0_TIMEOUT_MS ?? env.GENERATION_TIMEOUT_MS;
  if (typeof raw !== "string" || raw.trim().length === 0) {
    return fallbackMs;
  }

  const parsed = Number.parseInt(raw, 10);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return fallbackMs;
  }

  return parsed;
}
