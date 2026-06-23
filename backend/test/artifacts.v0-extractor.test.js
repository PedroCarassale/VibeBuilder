import test from "node:test";
import assert from "node:assert/strict";
import { extractV0ArtifactSource } from "../src/artifacts/v0-artifact-extractor.js";
import { createMinimalReactViteFiles } from "./helpers/mock-v0-files.js";

test("v0 extractor always enriches from getVersion", async () => {
  const files = createMinimalReactViteFiles({ title: "V0 App" });
  let getVersionCalled = false;
  const chatResult = {
    id: "chat-1",
    latestVersion: {
      id: "version-1",
      status: "completed",
      demoUrl: "https://preview.v0.dev/version-1",
      files: [{ name: "app/page.tsx", content: "export default function Page() { return null; }" }]
    }
  };

  const artifactSource = await extractV0ArtifactSource({
    client: {
      chats: {
        getVersion: async () => {
          getVersionCalled = true;
          return { files };
        }
      }
    },
    chatResult,
    generatorName: "v0"
  });

  assert.equal(getVersionCalled, true);
  assert.ok(artifactSource.files.some((file) => file.relativePath === "package.json"));
});

test("v0 extractor normalizes latestVersion files", async () => {
  const files = createMinimalReactViteFiles({ title: "V0 App" });
  const chatResult = {
    id: "chat-1",
    latestVersion: {
      id: "version-1",
      status: "completed",
      demoUrl: "https://preview.v0.dev/version-1",
      files
    }
  };

  const artifactSource = await extractV0ArtifactSource({
    client: null,
    chatResult,
    generatorName: "v0"
  });

  assert.equal(artifactSource.providerChatId, "chat-1");
  assert.equal(artifactSource.providerVersionId, "version-1");
  assert.equal(artifactSource.files.length, 3);
  assert.equal(artifactSource.previewUrl, "https://preview.v0.dev/version-1");
});

test("v0 extractor falls back to getVersion when files are empty", async () => {
  const files = createMinimalReactViteFiles();
  const client = {
    chats: {
      getVersion: async () => ({
        id: "version-2",
        status: "completed",
        files
      })
    }
  };

  const artifactSource = await extractV0ArtifactSource({
    client,
    chatResult: {
      id: "chat-2",
      latestVersion: {
        id: "version-2",
        status: "completed",
        demoUrl: "https://preview.v0.dev/version-2",
        files: []
      }
    }
  });

  assert.equal(artifactSource.files.length, 3);
});

test("v0 extractor drops binary assets from inline files", async () => {
  const files = [
    ...createMinimalReactViteFiles(),
    { name: "public/favicon.ico", content: "fake-icon-bytes" },
    { name: "public/hero.png", content: "fake-png-bytes" }
  ];
  const chatResult = {
    id: "chat-3",
    latestVersion: {
      id: "version-3",
      status: "completed",
      demoUrl: "https://preview.v0.dev/version-3",
      files
    }
  };

  const artifactSource = await extractV0ArtifactSource({
    client: null,
    chatResult
  });

  assert.equal(artifactSource.files.length, 3);
  assert.ok(!artifactSource.files.some((file) => file.relativePath.endsWith(".ico")));
  assert.ok(!artifactSource.files.some((file) => file.relativePath.endsWith(".png")));
});
