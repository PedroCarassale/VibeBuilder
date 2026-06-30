import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createLocalArtifactStorage } from "./local-artifact-storage.js";
import { createVercelBlobArtifactStorage } from "./vercel-blob-artifact-storage.js";

const DEFAULT_LOCAL_ROOT = path.resolve(
  fileURLToPath(new URL("../../data/artifacts", import.meta.url))
);
const DEFAULT_VERCEL_LOCAL_ROOT = path.join(os.tmpdir(), "vibebuilder-artifacts");

export function resolveArtifactStorage(options = {}) {
  const env = options.env ?? process.env;
  const explicitStorage = options.artifactStorage ?? null;
  if (explicitStorage) return explicitStorage;

  const blobToken =
    typeof env.BLOB_READ_WRITE_TOKEN === "string" ? env.BLOB_READ_WRITE_TOKEN.trim() : "";
  const onVercel = env.VERCEL === "1" || Boolean(env.VERCEL_ENV);

  if (blobToken.length > 0) {
    return createVercelBlobArtifactStorage({ token: blobToken });
  }

  if (onVercel) {
    console.warn(
      "[artifacts] BLOB_READ_WRITE_TOKEN is not set on Vercel; falling back to ephemeral local storage."
    );
  }

  const rootPath =
    typeof env.ARTIFACT_STORAGE_PATH === "string" && env.ARTIFACT_STORAGE_PATH.trim().length > 0
      ? env.ARTIFACT_STORAGE_PATH.trim()
      : onVercel
      ? DEFAULT_VERCEL_LOCAL_ROOT
      : DEFAULT_LOCAL_ROOT;

  return createLocalArtifactStorage({ rootPath });
}
