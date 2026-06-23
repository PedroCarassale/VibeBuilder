export const FRAMEWORK_REACT_VITE_TS = "react-vite-ts";
export const FRAMEWORK_NEXT_TS = "next-ts";
export const FRAMEWORK_V0_WEB = "v0-web";
export const TEMPLATE_VERSION = "1.0.0";
export const DEFAULT_ENTRY_POINT = "src/main.tsx";

export const DEFAULT_MAX_FILE_BYTES = 512_000;
export const DEFAULT_MAX_TOTAL_BYTES = 2_000_000;
export const DEFAULT_MAX_FILES = 200;

export const REQUIRED_CORE_FILES = ["package.json", "index.html", DEFAULT_ENTRY_POINT];

export const BINARY_FILE_EXTENSIONS = new Set([
  ".png",
  ".jpg",
  ".jpeg",
  ".gif",
  ".webp",
  ".ico",
  ".bmp",
  ".pdf",
  ".zip",
  ".tar",
  ".gz",
  ".wasm",
  ".exe",
  ".dll",
  ".so",
  ".dylib",
  ".mp3",
  ".mp4",
  ".woff",
  ".woff2",
  ".ttf",
  ".eot"
]);

export function isBinaryArtifactPath(relativePath) {
  if (typeof relativePath !== "string") return false;
  const extension = relativePath.includes(".")
    ? relativePath.slice(relativePath.lastIndexOf(".")).toLowerCase()
    : "";
  return BINARY_FILE_EXTENSIONS.has(extension);
}

export function filterTextOnlyArtifactFiles(files) {
  if (!Array.isArray(files)) return [];

  return files.filter((file) => {
    const relativePath = normalizeRelativePath(file?.relativePath ?? file?.name ?? file?.path);
    if (!relativePath) return false;
    return !isBinaryArtifactPath(relativePath);
  });
}

export function resolveArtifactLimits(env = process.env) {
  const maxFileBytes = parsePositiveInt(env.ARTIFACT_MAX_FILE_BYTES, DEFAULT_MAX_FILE_BYTES);
  const maxTotalBytes = parsePositiveInt(env.ARTIFACT_MAX_TOTAL_BYTES, DEFAULT_MAX_TOTAL_BYTES);
  const maxFiles = parsePositiveInt(env.ARTIFACT_MAX_FILES, DEFAULT_MAX_FILES);
  return { maxFileBytes, maxTotalBytes, maxFiles };
}

function parsePositiveInt(raw, fallback) {
  if (typeof raw !== "string" || raw.trim().length === 0) return fallback;
  const parsed = Number.parseInt(raw, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

export function normalizeRelativePath(rawPath) {
  if (typeof rawPath !== "string") return null;
  const trimmed = rawPath.trim().replace(/\\/g, "/");
  if (!trimmed || trimmed.startsWith("/") || trimmed.includes("\0")) return null;
  const segments = trimmed.split("/").filter((segment) => segment.length > 0);
  if (segments.some((segment) => segment === "." || segment === "..")) return null;
  return segments.join("/");
}

export function inferContentType(relativePath) {
  const lower = relativePath.toLowerCase();
  if (lower.endsWith(".json")) return "application/json";
  if (lower.endsWith(".html")) return "text/html; charset=utf-8";
  if (lower.endsWith(".css")) return "text/css; charset=utf-8";
  if (lower.endsWith(".tsx")) return "text/typescript; charset=utf-8";
  if (lower.endsWith(".ts")) return "text/typescript; charset=utf-8";
  if (lower.endsWith(".jsx")) return "text/javascript; charset=utf-8";
  if (lower.endsWith(".js")) return "text/javascript; charset=utf-8";
  if (lower.endsWith(".md")) return "text/markdown; charset=utf-8";
  return "text/plain; charset=utf-8";
}

export function parseDependencyManifest(packageJsonContent) {
  try {
    const parsed = JSON.parse(packageJsonContent);
    if (!parsed || typeof parsed !== "object") return { dependencies: {}, devDependencies: {} };
    return {
      dependencies:
        parsed.dependencies && typeof parsed.dependencies === "object" ? parsed.dependencies : {},
      devDependencies:
        parsed.devDependencies && typeof parsed.devDependencies === "object"
          ? parsed.devDependencies
          : {}
    };
  } catch {
    return null;
  }
}

export function detectProjectProfile(filePaths, packageJsonContent = null) {
  const paths = new Set(filePaths);
  let parsedPackage = null;
  if (typeof packageJsonContent === "string" && packageJsonContent.trim().length > 0) {
    try {
      parsedPackage = JSON.parse(packageJsonContent);
    } catch {
      parsedPackage = null;
    }
  }

  const dependencies = {
    ...(parsedPackage?.dependencies ?? {}),
    ...(parsedPackage?.devDependencies ?? {})
  };

  const isNext =
    "next" in dependencies ||
    paths.has("next.config.ts") ||
    paths.has("next.config.mjs") ||
    paths.has("next.config.js") ||
    [...paths].some((path) => path.startsWith("app/") && path.endsWith("page.tsx"));

  if (isNext) {
    const entryCandidates = [
      "app/page.tsx",
      "app/page.jsx",
      "pages/index.tsx",
      "pages/index.jsx",
      "src/app/page.tsx"
    ];
    const entryPoint =
      entryCandidates.find((path) => paths.has(path)) ??
      [...paths].find((path) => path.endsWith("page.tsx") || path.endsWith("page.jsx")) ??
      "app/page.tsx";

    return {
      framework: FRAMEWORK_NEXT_TS,
      entryPoint,
      requiredCoreFiles: ["package.json"]
    };
  }

  const isVite =
    "vite" in dependencies ||
    paths.has("vite.config.ts") ||
    paths.has("vite.config.js") ||
    paths.has("vite.config.mjs");

  if (isVite) {
    const entryCandidates = [
      DEFAULT_ENTRY_POINT,
      "src/main.ts",
      "src/main.jsx",
      "src/index.tsx"
    ];
    const entryPoint = entryCandidates.find((path) => paths.has(path)) ?? DEFAULT_ENTRY_POINT;
    const requiredCoreFiles = ["package.json"];
    if (paths.has("index.html")) {
      requiredCoreFiles.push("index.html");
    }
    if (paths.has(entryPoint)) {
      requiredCoreFiles.push(entryPoint);
    }

    return {
      framework: FRAMEWORK_REACT_VITE_TS,
      entryPoint,
      requiredCoreFiles: [...new Set(requiredCoreFiles)]
    };
  }

  const entryPoint =
    [...paths].find(
      (path) =>
        path.endsWith("page.tsx") ||
        path.endsWith("main.tsx") ||
        path.endsWith("index.tsx") ||
        path.endsWith("App.tsx")
    ) ?? "package.json";

  return {
    framework: FRAMEWORK_V0_WEB,
    entryPoint,
    requiredCoreFiles: ["package.json"]
  };
}
