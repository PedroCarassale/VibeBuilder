import { strToU8, zipSync } from "fflate";

export async function buildArtifactZipBuffer({ files, artifactStorage }) {
  const zipEntries = {};

  for (const file of files) {
    const content = await artifactStorage.getFile(file.storage_key);
    zipEntries[file.relative_path] = typeof content === "string" ? strToU8(content) : content;
  }

  return Buffer.from(zipSync(zipEntries, { level: 9 }));
}

export async function streamArtifactZip({ response, files, artifactStorage, archiveName }) {
  const buffer = await buildArtifactZipBuffer({ files, artifactStorage });
  response.statusCode = 200;
  response.setHeader("Content-Type", "application/zip");
  response.setHeader(
    "Content-Disposition",
    `attachment; filename="${archiveName.replace(/"/g, "")}"`
  );
  response.end(buffer);
}
