import { createBackendServer, loadBackendEnv } from "./bootstrap.js";

loadBackendEnv();

const PORT = Number.parseInt(process.env.PORT ?? "3000", 10);
const { server, generationProvider } = await createBackendServer();

server.listen(PORT, () => {
  console.log(
    `VibeBuilder backend listening on port ${PORT} (provider=${generationProvider.name})`
  );
});
