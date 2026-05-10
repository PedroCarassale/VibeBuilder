import test from "node:test";
import assert from "node:assert/strict";
import {
  ProviderTimeoutError,
  V0ProviderError,
  createV0Provider,
  generateWithTimeout
} from "../src/generation-provider.js";

function buildFakeClient(behavior) {
  const calls = [];
  const create = async (params) => {
    calls.push(params);
    return behavior(params);
  };
  return {
    calls,
    client: { chats: { create } }
  };
}

test("createV0Provider expone name 'v0' y delega en chats.create", async () => {
  const fake = buildFakeClient(async () => ({
    id: "chat-123",
    object: "chat",
    webUrl: "https://v0.dev/chat/chat-123",
    apiUrl: "https://api.v0.dev/v1/chats/chat-123",
    latestVersion: {
      id: "version-1",
      object: "version",
      status: "completed",
      demoUrl: "https://preview.v0.dev/abc"
    },
    modelConfiguration: {
      modelId: "v0-1.5-md"
    }
  }));

  const provider = createV0Provider({ client: fake.client });
  assert.equal(provider.name, "v0");

  const result = await provider.generate({ prompt: "Crea una landing" });

  assert.equal(fake.calls.length, 1);
  assert.equal(fake.calls[0].message, "Crea una landing");

  assert.equal(result.providerMeta.provider, "v0");
  assert.equal(result.providerMeta.requestId, "chat-123");
  assert.equal(result.providerMeta.model, "v0-1.5-md");
  assert.equal(result.providerMeta.finishReason, "completed");
  assert.equal(result.providerMeta.previewUrl, "https://preview.v0.dev/abc");
  assert.equal(result.providerMeta.promptLength, "Crea una landing".length);
  assert.equal(typeof result.providerMeta.latencyMs, "number");
  assert.ok(result.providerMeta.latencyMs >= 0);
});

test("createV0Provider sin apiKey ni client lanza error explicito", () => {
  assert.throws(() => createV0Provider({}), /V0_API_KEY is required/);
  assert.throws(() => createV0Provider({ apiKey: "   " }), /V0_API_KEY is required/);
});

test("createV0Provider clasifica errores HTTP del SDK como V0ProviderError", async () => {
  const fake = buildFakeClient(async () => {
    throw new Error("HTTP 401: Unauthorized - invalid token");
  });
  const provider = createV0Provider({ client: fake.client });

  await assert.rejects(
    () => provider.generate({ prompt: "Hola" }),
    (error) => {
      assert.ok(error instanceof V0ProviderError);
      assert.equal(error.status, 401);
      assert.equal(error.retryable, false);
      assert.equal(error.message, "v0 provider request failed.");
      return true;
    }
  );
});

test("createV0Provider marca como retryable los 5xx y 429", async () => {
  const cases = [
    { httpStatus: 429, retryable: true },
    { httpStatus: 500, retryable: true },
    { httpStatus: 503, retryable: true },
    { httpStatus: 400, retryable: false },
    { httpStatus: 404, retryable: false }
  ];

  for (const scenario of cases) {
    const fake = buildFakeClient(async () => {
      throw new Error(`HTTP ${scenario.httpStatus}: details`);
    });
    const provider = createV0Provider({ client: fake.client });

    await assert.rejects(
      () => provider.generate({ prompt: "x" }),
      (error) => {
        assert.ok(error instanceof V0ProviderError);
        assert.equal(error.status, scenario.httpStatus);
        assert.equal(error.retryable, scenario.retryable);
        return true;
      }
    );
  }
});

test("createV0Provider clasifica errores sin status como retryable PROVIDER_ERROR", async () => {
  const fake = buildFakeClient(async () => {
    throw new Error("network connection reset");
  });
  const provider = createV0Provider({ client: fake.client });

  await assert.rejects(
    () => provider.generate({ prompt: "x" }),
    (error) => {
      assert.ok(error instanceof V0ProviderError);
      assert.equal(error.status, null);
      assert.equal(error.retryable, true);
      return true;
    }
  );
});

test("createV0Provider responde a AbortSignal con AbortError (sin envolver)", async () => {
  const fake = buildFakeClient(
    () =>
      new Promise(() => {
        // hang forever
      })
  );
  const provider = createV0Provider({ client: fake.client });
  const controller = new AbortController();

  const generationPromise = provider.generate({
    prompt: "lento",
    signal: controller.signal
  });

  setTimeout(() => controller.abort(), 10);

  await assert.rejects(generationPromise, (error) => {
    assert.equal(error.name, "AbortError");
    return true;
  });
});

test("generateWithTimeout convierte AbortError del proveedor v0 en ProviderTimeoutError", async () => {
  const fake = buildFakeClient(
    () =>
      new Promise(() => {
        // never resolves
      })
  );
  const provider = createV0Provider({ client: fake.client });

  await assert.rejects(
    () =>
      generateWithTimeout({
        provider,
        payload: { prompt: "lento" },
        timeoutMs: 10
      }),
    ProviderTimeoutError
  );
});

test("createV0Provider lanza V0ProviderError si el client carece de chats.create", async () => {
  const provider = createV0Provider({ client: { chats: {} } });

  await assert.rejects(
    () => provider.generate({ prompt: "x" }),
    (error) => {
      assert.ok(error instanceof V0ProviderError);
      assert.equal(error.retryable, false);
      return true;
    }
  );
});

test("createV0Provider usa clientFactory de forma perezosa solo una vez", async () => {
  let factoryCalls = 0;
  const fake = buildFakeClient(async () => ({
    id: "chat-1",
    latestVersion: { status: "completed" }
  }));

  const provider = createV0Provider({
    clientFactory: async () => {
      factoryCalls += 1;
      return fake.client;
    }
  });

  await provider.generate({ prompt: "uno" });
  await provider.generate({ prompt: "dos" });

  assert.equal(factoryCalls, 1);
  assert.equal(fake.calls.length, 2);
});

test("createV0Provider permite buildMessage para customizar el mensaje al SDK", async () => {
  const fake = buildFakeClient(async () => ({
    id: "chat-x",
    latestVersion: { status: "completed" }
  }));
  const provider = createV0Provider({
    client: fake.client,
    buildMessage: (prompt) => `[VibeBuilder] ${prompt}`
  });

  await provider.generate({ prompt: "Crea un dashboard" });

  assert.equal(fake.calls[0].message, "[VibeBuilder] Crea un dashboard");
});

test("createV0Provider no expone secretos del prompt en providerMeta", async () => {
  const fake = buildFakeClient(async () => ({
    id: "chat-secret",
    apiKey: "should-not-leak",
    secretToken: "should-not-leak",
    latestVersion: { status: "completed" }
  }));
  const provider = createV0Provider({ client: fake.client });

  const result = await provider.generate({ prompt: "x" });

  assert.equal("apiKey" in result.providerMeta, false);
  assert.equal("secretToken" in result.providerMeta, false);
});
