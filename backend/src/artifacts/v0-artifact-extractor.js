import { normalizeRelativePath, detectProjectProfile, filterTextOnlyArtifactFiles } from "./contract.js";
import { extractTextFilesFromZipBuffer } from "./v0-zip-files.js";

function pickStringField(source, fields) {
  if (!source || typeof source !== "object") return null;
  for (const field of fields) {
    const value = source[field];
    if (typeof value === "string" && value.trim().length > 0) {
      return value.trim();
    }
  }
  return null;
}

export function normalizeV0Files(files) {
  if (!Array.isArray(files)) return [];

  const normalized = [];
  for (const file of files) {
    const relativePath = normalizeRelativePath(file?.name ?? file?.path ?? file?.relativePath);
    const content = typeof file?.content === "string" ? file.content : null;
    if (!relativePath || content === null) continue;
    normalized.push({ relativePath, content });
  }
  return normalized;
}

function mergeFilesByPath(...fileLists) {
  const merged = new Map();
  for (const fileList of fileLists) {
    for (const file of fileList) {
      if (!file?.relativePath) continue;
      merged.set(file.relativePath, file);
    }
  }
  return [...merged.values()];
}

function needsZipFallback(files) {
  if (!files.some((file) => file.relativePath === "package.json")) {
    return true;
  }

  const packageFile = files.find((file) => file.relativePath === "package.json");
  const profile = detectProjectProfile(
    files.map((file) => file.relativePath),
    packageFile?.content ?? null
  );

  return profile.requiredCoreFiles.some((requiredPath) => {
    return !files.some((file) => file.relativePath === requiredPath);
  });
}

async function downloadVersionFiles(client, chatId, versionId) {
  if (!client?.chats || typeof client.chats.downloadVersion !== "function") {
    return [];
  }

  const archive = await client.chats.downloadVersion({
    chatId,
    versionId,
    format: "zip",
    includeDefaultFiles: true
  });

  return extractTextFilesFromZipBuffer(archive);
}

export async function extractV0ArtifactSource({
  client,
  chatResult,
  generatorName = "v0",
  generatorVersion = null
}) {
  const chatId = pickStringField(chatResult, ["id"]);
  const latestVersion = chatResult?.latestVersion;
  const versionId = pickStringField(latestVersion, ["id"]);
  const previewUrl = pickStringField(latestVersion, ["demoUrl"]);
  const versionStatus = pickStringField(latestVersion, ["status"]);

  if (!chatId || !versionId) {
    throw new Error("v0 response is missing chat or version identifiers.");
  }

  const filesFromLatest = normalizeV0Files(latestVersion?.files);
  let filesFromVersion = [];

  if (client?.chats && typeof client.chats.getVersion === "function") {
    const versionDetail = await client.chats.getVersion({
      chatId,
      versionId,
      includeDefaultFiles: true
    });
    filesFromVersion = normalizeV0Files(versionDetail?.files);
  }

  let files = mergeFilesByPath(filesFromLatest, filesFromVersion);

  if (needsZipFallback(files)) {
    const zipFiles = await downloadVersionFiles(client, chatId, versionId);
    files = mergeFilesByPath(files, zipFiles);
  }

  files = filterTextOnlyArtifactFiles(files);

  if (files.length === 0) {
    throw new Error(
      versionStatus && versionStatus !== "completed"
        ? `v0 version is not completed (status=${versionStatus}).`
        : "v0 version did not return any files."
    );
  }

  return {
    type: "inline-files",
    generatorName,
    generatorVersion,
    providerChatId: chatId,
    providerVersionId: versionId,
    previewUrl,
    files
  };
}

export function artifactSourceFromInline({
  files,
  generatorName,
  generatorVersion = null,
  providerChatId = null,
  providerVersionId = null,
  previewUrl = null
}) {
  return {
    type: "inline-files",
    generatorName,
    generatorVersion,
    providerChatId,
    providerVersionId,
    previewUrl,
    files
  };
}
