/**
 * Llama a la Platform API de v0 con una key concreta (sin persistir).
 * Usa `user.get()` como comprobación ligera documentada en v0-sdk.
 */
export async function testV0ApiConnection({ apiKey, baseUrl }) {
  if (typeof apiKey !== "string" || !apiKey.trim()) {
    throw new Error("apiKey is required.");
  }

  const sdk = await import("v0-sdk");
  const factory = sdk.createClient ?? sdk.default?.createClient;
  if (typeof factory !== "function") {
    throw new Error("v0-sdk does not expose createClient().");
  }

  const client = factory({
    apiKey: apiKey.trim(),
    ...(typeof baseUrl === "string" && baseUrl.trim().length > 0
      ? { baseUrl: baseUrl.trim() }
      : {})
  });

  if (!client?.user || typeof client.user.get !== "function") {
    throw new Error("v0 client is missing user.get().");
  }

  await client.user.get();
  return { ok: true };
}
