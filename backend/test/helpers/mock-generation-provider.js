import { createMockArtifactSource } from "../../src/artifacts/mock-artifact-source.js";

export function withMockArtifactResult(result, prompt = "mock prompt") {
  const previewUrl =
    typeof result?.providerMeta?.previewUrl === "string"
      ? result.providerMeta.previewUrl
      : undefined;

  return {
    assistantText: "",
    ...result,
    artifactSource:
      result?.artifactSource ??
      createMockArtifactSource({
        prompt,
        previewUrl
      })
  };
}

export function createArtifactAwareMockProvider(handler) {
  return {
    name: "mock-v0",
    async generate(payload) {
      const result = await handler(payload);
      return withMockArtifactResult(result, payload?.prompt ?? "");
    }
  };
}
