import test from "node:test";
import assert from "node:assert/strict";
import { buildManifestFromFiles } from "../src/artifacts/manifest-validator.js";
import { filterTextOnlyArtifactFiles } from "../src/artifacts/contract.js";
import { createMinimalReactViteFiles } from "./helpers/mock-v0-files.js";

test("manifest validator accepts a minimal react-vite project", () => {
  const manifest = buildManifestFromFiles(
    createMinimalReactViteFiles().map((file) => ({
      relativePath: file.name,
      content: file.content
    }))
  );

  assert.equal(manifest.framework, "react-vite-ts");
  assert.equal(manifest.entryPoint, "src/main.tsx");
  assert.equal(manifest.fileCount, 3);
});

test("manifest validator accepts a minimal next.js project", () => {
  const manifest = buildManifestFromFiles([
    {
      relativePath: "package.json",
      content: JSON.stringify(
        {
          name: "next-app",
          private: true,
          dependencies: { next: "15.0.0", react: "^18.3.1", "react-dom": "^18.3.1" }
        },
        null,
        2
      )
    },
    {
      relativePath: "app/page.tsx",
      content: "export default function Page() { return <main>Hi</main>; }"
    },
    {
      relativePath: "next.config.ts",
      content: "export default {}"
    }
  ]);

  assert.equal(manifest.framework, "next-ts");
  assert.equal(manifest.entryPoint, "app/page.tsx");
});

test("manifest validator rejects path traversal", () => {
  const files = createMinimalReactViteFiles();
  files.push({ name: "../secret.txt", content: "nope" });

  assert.throws(
    () =>
      buildManifestFromFiles(
        files.map((file) => ({ relativePath: file.name, content: file.content }))
      ),
    /invalid relative path/i
  );
});

test("manifest validator rejects duplicate paths", () => {
  const files = createMinimalReactViteFiles();
  files.push({ name: "index.html", content: "<html></html>" });

  assert.throws(
    () =>
      buildManifestFromFiles(
        files.map((file) => ({ relativePath: file.name, content: file.content }))
      ),
    /Duplicate file path/
  );
});

test("filterTextOnlyArtifactFiles strips binary assets before validation", () => {
  const files = createMinimalReactViteFiles();
  files.push({ name: "assets/logo.png", content: "fake" });

  const manifest = buildManifestFromFiles(
    filterTextOnlyArtifactFiles(
      files.map((file) => ({ relativePath: file.name, content: file.content }))
    )
  );

  assert.equal(manifest.fileCount, 3);
});

test("manifest validator rejects missing core files", () => {
  assert.throws(
    () =>
      buildManifestFromFiles([
        { relativePath: "index.html", content: "<html></html>" },
        { relativePath: "src/main.tsx", content: "export {}" }
      ]),
    /Required file is missing/
  );
});
