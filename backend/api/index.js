import { getOrCreateServer } from "../src/bootstrap.js";

/**
 * Entrada serverless de Vercel: reenvía cada request al mismo http.Server que usa `npm start`.
 */
export default function handler(request, response) {
  const server = getOrCreateServer();
  server.emit("request", request, response);
}
