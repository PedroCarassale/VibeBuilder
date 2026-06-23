import fs from "node:fs";
import fsp from "node:fs/promises";
import path from "node:path";
import { Readable } from "node:stream";

export function createLocalArtifactStorage({ rootPath }) {
  const resolvedRoot = path.resolve(rootPath);
  fs.mkdirSync(resolvedRoot, { recursive: true });

  function resolveStoragePath(storageKey) {
    const normalizedKey = storageKey.replace(/\\/g, "/").replace(/^\/+/, "");
    const absolutePath = path.resolve(resolvedRoot, normalizedKey);
    if (!absolutePath.startsWith(resolvedRoot + path.sep) && absolutePath !== resolvedRoot) {
      throw new Error("Invalid storage key.");
    }
    return absolutePath;
  }

  return {
    name: "local",
    async putFile(storageKey, content, _contentType) {
      const absolutePath = resolveStoragePath(storageKey);
      await fsp.mkdir(path.dirname(absolutePath), { recursive: true });
      await fsp.writeFile(absolutePath, content, "utf8");
    },
    async getFile(storageKey) {
      const absolutePath = resolveStoragePath(storageKey);
      return fsp.readFile(absolutePath, "utf8");
    },
    openReadStream(storageKey) {
      const absolutePath = resolveStoragePath(storageKey);
      return Readable.from(fs.createReadStream(absolutePath));
    },
    async deletePrefix(prefix) {
      const absolutePrefix = resolveStoragePath(prefix.replace(/\/+$/, ""));
      await fsp.rm(absolutePrefix, { recursive: true, force: true });
    }
  };
}
