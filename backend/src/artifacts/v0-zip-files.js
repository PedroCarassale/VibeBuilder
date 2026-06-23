import { unzipSync } from "fflate";
import { BINARY_FILE_EXTENSIONS, normalizeRelativePath } from "./contract.js";

const TEXT_EXTENSIONS = new Set([
  ".json",
  ".html",
  ".css",
  ".scss",
  ".md",
  ".txt",
  ".ts",
  ".tsx",
  ".js",
  ".jsx",
  ".mjs",
  ".cjs",
  ".yml",
  ".yaml",
  ".env",
  ".svg"
]);

function isTextLikePath(relativePath) {
  const lower = relativePath.toLowerCase();
  const extension = lower.includes(".") ? lower.slice(lower.lastIndexOf(".")) : "";
  if (BINARY_FILE_EXTENSIONS.has(extension)) return false;
  if (TEXT_EXTENSIONS.has(extension)) return true;
  if (!extension) return true;
  return false;
}

export function extractTextFilesFromZipBuffer(buffer) {
  const bytes =
    buffer instanceof ArrayBuffer
      ? new Uint8Array(buffer)
      : Buffer.isBuffer(buffer)
      ? new Uint8Array(buffer)
      : buffer;

  const entries = unzipSync(bytes);
  const files = [];

  for (const [rawPath, content] of Object.entries(entries)) {
    if (rawPath.endsWith("/")) continue;
    const relativePath = normalizeRelativePath(rawPath);
    if (!relativePath || !isTextLikePath(relativePath)) continue;
    files.push({
      relativePath,
      content: new TextDecoder("utf-8").decode(content)
    });
  }

  return files;
}
