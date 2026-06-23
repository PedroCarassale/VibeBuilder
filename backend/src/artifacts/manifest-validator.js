import { createHash } from "node:crypto";
import {
  BINARY_FILE_EXTENSIONS,
  detectProjectProfile,
  inferContentType,
  normalizeRelativePath,
  parseDependencyManifest,
  resolveArtifactLimits
} from "./contract.js";

export class ManifestValidationError extends Error {
  constructor({ code, message, details = null }) {
    super(message);
    this.name = "ManifestValidationError";
    this.code = code;
    this.details = details;
  }
}

export function buildManifestFromFiles(files, options = {}) {
  const limits = options.limits ?? resolveArtifactLimits(options.env);
  const normalizedFiles = [];
  const seenPaths = new Set();

  if (!Array.isArray(files) || files.length === 0) {
    throw new ManifestValidationError({
      code: "EMPTY_MANIFEST",
      message: "Artifact manifest must contain at least one file."
    });
  }

  if (files.length > limits.maxFiles) {
    throw new ManifestValidationError({
      code: "TOO_MANY_FILES",
      message: `Artifact exceeds maximum file count (${limits.maxFiles}).`
    });
  }

  let totalBytes = 0;

  for (const file of files) {
    const relativePath = normalizeRelativePath(file?.relativePath ?? file?.name ?? file?.path);
    if (!relativePath) {
      throw new ManifestValidationError({
        code: "UNSAFE_PATH",
        message: "Artifact contains an invalid relative path."
      });
    }

    if (seenPaths.has(relativePath)) {
      throw new ManifestValidationError({
        code: "DUPLICATE_PATH",
        message: `Duplicate file path: ${relativePath}`
      });
    }
    seenPaths.add(relativePath);

    const extension = relativePath.includes(".")
      ? relativePath.slice(relativePath.lastIndexOf(".")).toLowerCase()
      : "";
    if (BINARY_FILE_EXTENSIONS.has(extension)) {
      throw new ManifestValidationError({
        code: "BINARY_FILE",
        message: `Binary files are not supported: ${relativePath}`
      });
    }

    const content = typeof file?.content === "string" ? file.content : null;
    if (content === null) {
      throw new ManifestValidationError({
        code: "MISSING_CONTENT",
        message: `File content is required: ${relativePath}`
      });
    }

    const sizeBytes = Buffer.byteLength(content, "utf8");
    if (sizeBytes > limits.maxFileBytes) {
      throw new ManifestValidationError({
        code: "FILE_TOO_LARGE",
        message: `File exceeds size limit: ${relativePath}`
      });
    }

    totalBytes += sizeBytes;
    if (totalBytes > limits.maxTotalBytes) {
      throw new ManifestValidationError({
        code: "TOTAL_TOO_LARGE",
        message: "Artifact exceeds total size limit."
      });
    }

    normalizedFiles.push({
      relativePath,
      content,
      sizeBytes,
      checksumSha256: createHash("sha256").update(content, "utf8").digest("hex"),
      contentType: inferContentType(relativePath)
    });
  }

  const packageFile = normalizedFiles.find((file) => file.relativePath === "package.json");
  const profile = detectProjectProfile(
    [...seenPaths],
    packageFile?.content ?? null
  );
  const requiredCoreFiles = options.requiredCoreFiles ?? profile.requiredCoreFiles;
  const entryPoint = options.entryPoint ?? profile.entryPoint;

  for (const requiredPath of requiredCoreFiles) {
    if (!seenPaths.has(requiredPath)) {
      throw new ManifestValidationError({
        code: "MISSING_CORE_FILE",
        message: `Required file is missing: ${requiredPath}`
      });
    }
  }

  if (entryPoint !== "package.json" && !seenPaths.has(entryPoint)) {
    throw new ManifestValidationError({
      code: "MISSING_ENTRY_POINT",
      message: `Entry point is missing: ${entryPoint}`
    });
  }

  if (!packageFile) {
    throw new ManifestValidationError({
      code: "MISSING_CORE_FILE",
      message: "Required file is missing: package.json"
    });
  }

  const dependencyManifest = parseDependencyManifest(packageFile.content);
  if (!dependencyManifest) {
    throw new ManifestValidationError({
      code: "INVALID_PACKAGE_JSON",
      message: "package.json must be valid JSON."
    });
  }

  return {
    framework: profile.framework,
    entryPoint,
    files: normalizedFiles,
    dependencyManifest,
    fileCount: normalizedFiles.length,
    totalBytes
  };
}

export function validateManifest(manifest) {
  return buildManifestFromFiles(
    manifest.files.map((file) => ({
      relativePath: file.relativePath,
      content: file.content
    })),
    { entryPoint: manifest.entryPoint }
  );
}
