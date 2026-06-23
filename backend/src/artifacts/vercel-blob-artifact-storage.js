import { del, get, list, put } from "@vercel/blob";
import { Readable } from "node:stream";

export function createVercelBlobArtifactStorage({ token }) {
  if (typeof token !== "string" || token.trim().length === 0) {
    throw new Error("BLOB_READ_WRITE_TOKEN is required for Vercel Blob storage.");
  }

  const blobToken = token.trim();

  return {
    name: "vercel-blob",
    async putFile(storageKey, content, contentType) {
      await put(storageKey, content, {
        access: "public",
        token: blobToken,
        contentType: contentType ?? "text/plain; charset=utf-8",
        addRandomSuffix: false,
        allowOverwrite: true
      });
    },
    async getFile(storageKey) {
      const result = await get(storageKey, { token: blobToken });
      if (!result || result.statusCode !== 200 || !result.stream) {
        throw new Error(`Blob not found: ${storageKey}`);
      }
      const chunks = [];
      for await (const chunk of result.stream) {
        chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
      }
      return Buffer.concat(chunks).toString("utf8");
    },
    openReadStream(storageKey) {
      return Readable.from(
        (async function* () {
          const result = await get(storageKey, { token: blobToken });
          if (!result || result.statusCode !== 200 || !result.stream) {
            throw new Error(`Blob not found: ${storageKey}`);
          }
          for await (const chunk of result.stream) {
            yield Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
          }
        })()
      );
    },
    async deletePrefix(prefix) {
      let cursor;
      do {
        const page = await list({ prefix, token: blobToken, cursor });
        const urls = page.blobs.map((blob) => blob.url);
        if (urls.length > 0) {
          await del(urls, { token: blobToken });
        }
        cursor = page.hasMore ? page.cursor : undefined;
      } while (cursor);
    }
  };
}
