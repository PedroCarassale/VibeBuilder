let zipArchiveConstructorPromise = null;

async function loadZipArchiveConstructor() {
  zipArchiveConstructorPromise ??= import("archiver").then(({ ZipArchive }) => {
    if (typeof ZipArchive !== "function") {
      throw new Error("archiver did not export ZipArchive.");
    }
    return ZipArchive;
  });

  return zipArchiveConstructorPromise;
}

export async function buildArtifactZipBuffer({ files, artifactStorage }) {
  const ZipArchive = await loadZipArchiveConstructor();

  return new Promise((resolve, reject) => {
    const archive = new ZipArchive({ zlib: { level: 9 } });
    const chunks = [];

    archive.on("data", (chunk) => {
      chunks.push(chunk);
    });
    archive.on("error", reject);
    archive.on("end", () => {
      resolve(Buffer.concat(chunks));
    });

    (async () => {
      try {
        for (const file of files) {
          const content = await artifactStorage.getFile(file.storage_key);
          archive.append(content, { name: file.relative_path });
        }
        archive.finalize();
      } catch (error) {
        reject(error);
      }
    })();
  });
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
