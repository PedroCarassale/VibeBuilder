import { randomUUID } from "node:crypto";
import { TEMPLATE_VERSION, filterTextOnlyArtifactFiles } from "./contract.js";
import { ManifestValidationError, buildManifestFromFiles } from "./manifest-validator.js";

export class ArtifactPersistenceError extends Error {
  constructor({ code, message, cause = null }) {
    super(message);
    this.name = "ArtifactPersistenceError";
    this.code = code;
    this.cause = cause;
  }
}

export function createArtifactService({ db, artifactStorage }) {
  const findOwnedProjectStatement = db.prepare(`
    SELECT id
    FROM projects
    WHERE id = ? AND session_id = ? AND deleted_at IS NULL;
  `);

  const findArtifactByVersionStatement = db.prepare(`
    SELECT
      a.id,
      a.version_id,
      a.framework,
      a.template_version,
      a.entry_point,
      a.generator_name,
      a.generator_version,
      a.provider_chat_id,
      a.provider_version_id,
      a.validation_status,
      a.validation_report,
      a.preview_ref,
      a.dependency_manifest,
      a.file_count,
      a.total_bytes,
      a.finalized_at,
      a.created_at
    FROM version_artifacts a
    INNER JOIN project_versions v ON v.id = a.version_id
    WHERE v.project_id = ?
      AND v.version_number = ?;
  `);

  const listArtifactFilesStatement = db.prepare(`
    SELECT relative_path, size_bytes, checksum_sha256, content_type, storage_key
    FROM version_artifact_files
    WHERE artifact_id = ?
    ORDER BY relative_path ASC;
  `);

  const listVersionArtifactsStatement = db.prepare(`
    SELECT
      v.id AS version_id,
      v.version_number,
      a.framework,
      a.file_count,
      a.total_bytes,
      a.validation_status,
      a.finalized_at
    FROM project_versions v
    LEFT JOIN version_artifacts a ON a.version_id = v.id
    WHERE v.project_id = ?
    ORDER BY v.version_number DESC, v.created_at DESC, v.id DESC
    LIMIT 20;
  `);

  const insertArtifactStatementSql = `
    INSERT INTO version_artifacts (
      id,
      version_id,
      framework,
      template_version,
      entry_point,
      generator_name,
      generator_version,
      provider_chat_id,
      provider_version_id,
      validation_status,
      validation_report,
      preview_ref,
      dependency_manifest,
      file_count,
      total_bytes,
      finalized_at,
      created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
  `;

  const insertArtifactFileStatementSql = `
    INSERT INTO version_artifact_files (
      id,
      artifact_id,
      relative_path,
      size_bytes,
      checksum_sha256,
      storage_key,
      content_type,
      created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
  `;

  function buildStorageKey(artifactId, relativePath) {
    return `artifacts/${artifactId}/${relativePath}`;
  }

  function mapArtifactSummary(row) {
    if (!row || !row.framework) return null;
    return {
      framework: row.framework,
      fileCount: row.file_count,
      totalBytes: row.total_bytes,
      validationStatus: row.validation_status,
      hasExport: row.validation_status === "structural_ok"
    };
  }

  function mapArtifactDetail(row, files) {
    if (!row) return null;

    let dependencyManifest = null;
    if (typeof row.dependency_manifest === "string" && row.dependency_manifest.length > 0) {
      try {
        dependencyManifest = JSON.parse(row.dependency_manifest);
      } catch {
        dependencyManifest = null;
      }
    }

    let validationReport = null;
    if (typeof row.validation_report === "string" && row.validation_report.length > 0) {
      try {
        validationReport = JSON.parse(row.validation_report);
      } catch {
        validationReport = null;
      }
    }

    return {
      id: row.id,
      versionId: row.version_id,
      framework: row.framework,
      templateVersion: row.template_version,
      entryPoint: row.entry_point,
      generatorName: row.generator_name,
      generatorVersion: row.generator_version,
      validationStatus: row.validation_status,
      validationReport,
      previewRef: row.preview_ref,
      dependencyManifest,
      fileCount: row.file_count,
      totalBytes: row.total_bytes,
      finalizedAt: row.finalized_at,
      createdAt: row.created_at,
      files: files.map((file) => ({
        relativePath: file.relative_path,
        sizeBytes: file.size_bytes,
        checksumSha256: file.checksum_sha256,
        contentType: file.content_type
      }))
    };
  }

  async function prepareArtifactPayload({ projectId, sessionId, artifactSource }) {
    const ownedProject = await findOwnedProjectStatement.get(projectId, sessionId);
    if (!ownedProject) {
      throw new ArtifactPersistenceError({
        code: "PROJECT_NOT_FOUND",
        message: "Project not found for this session."
      });
    }

    let manifest;
    try {
      manifest = buildManifestFromFiles(
        filterTextOnlyArtifactFiles(artifactSource.files ?? [])
      );
    } catch (error) {
      if (error instanceof ManifestValidationError) {
        throw new ArtifactPersistenceError({
          code: error.code,
          message: error.message,
          cause: error
        });
      }
      throw error;
    }

    const artifactId = randomUUID();
    const storagePrefix = `artifacts/${artifactId}`;

    try {
      for (const file of manifest.files) {
        const storageKey = buildStorageKey(artifactId, file.relativePath);
        await artifactStorage.putFile(storageKey, file.content, file.contentType);
      }
    } catch (error) {
      throw new ArtifactPersistenceError({
        code: "ARTIFACT_STORAGE_FAILED",
        message: "Could not upload artifact files.",
        cause: error
      });
    }

    return {
      artifactId,
      storagePrefix,
      manifest,
      artifactSource
    };
  }

  async function insertPreparedArtifact(tx, { versionId, prepared }) {
    const now = new Date().toISOString();
    const { artifactId, manifest, artifactSource } = prepared;

    await tx.prepare(insertArtifactStatementSql).run(
      artifactId,
      versionId,
      manifest.framework,
      TEMPLATE_VERSION,
      manifest.entryPoint,
      artifactSource.generatorName ?? null,
      artifactSource.generatorVersion ?? null,
      artifactSource.providerChatId ?? null,
      artifactSource.providerVersionId ?? null,
      "structural_ok",
      JSON.stringify({ status: "structural_ok" }),
      artifactSource.previewUrl ?? null,
      JSON.stringify(manifest.dependencyManifest),
      manifest.fileCount,
      manifest.totalBytes,
      now,
      now
    );

    for (const file of manifest.files) {
      await tx.prepare(insertArtifactFileStatementSql).run(
        randomUUID(),
        artifactId,
        file.relativePath,
        file.sizeBytes,
        file.checksumSha256,
        buildStorageKey(artifactId, file.relativePath),
        file.contentType,
        now
      );
    }

    return {
      artifactId,
      validationStatus: "structural_ok",
      previewRef: artifactSource.previewUrl ?? null,
      fileCount: manifest.fileCount,
      totalBytes: manifest.totalBytes,
      framework: manifest.framework
    };
  }

  async function cleanupPreparedArtifact(prepared) {
    if (!prepared?.storagePrefix) return;
    try {
      await artifactStorage.deletePrefix(prepared.storagePrefix);
    } catch {
      // best effort
    }
  }

  async function getArtifactSummaryForVersions(projectId) {
    const rows = await listVersionArtifactsStatement.all(projectId);
    return new Map(
      rows.map((row) => [
        row.version_id,
        {
          versionId: row.version_id,
          versionNumber: row.version_number,
          artifact: mapArtifactSummary(row)
        }
      ])
    );
  }

  async function getVersionArtifactDetail({ projectId, versionNumber, sessionId }) {
    const ownedProject = await findOwnedProjectStatement.get(projectId, sessionId);
    if (!ownedProject) return null;

    const artifactRow = await findArtifactByVersionStatement.get(projectId, versionNumber);
    if (!artifactRow) return null;

    const files = await listArtifactFilesStatement.all(artifactRow.id);
    return mapArtifactDetail(artifactRow, files);
  }

  async function openVersionExport({ projectId, versionNumber, sessionId }) {
    const detail = await getVersionArtifactDetail({ projectId, versionNumber, sessionId });
    if (!detail || detail.validationStatus !== "structural_ok") return null;

    const artifactRow = await findArtifactByVersionStatement.get(projectId, versionNumber);
    const files = await listArtifactFilesStatement.all(artifactRow.id);
    return { detail, files };
  }

  return {
    prepareArtifactPayload,
    insertPreparedArtifact,
    cleanupPreparedArtifact,
    getArtifactSummaryForVersions,
    getVersionArtifactDetail,
    openVersionExport,
    artifactStorage
  };
}
