import { DEFAULT_ENTRY_POINT } from "../../src/artifacts/contract.js";

export function createMinimalReactViteFiles({ title = "Test App" } = {}) {
  return [
    {
      name: "package.json",
      content: JSON.stringify(
        {
          name: "test-app",
          private: true,
          version: "0.0.1",
          type: "module",
          scripts: { dev: "vite", build: "vite build" },
          dependencies: { react: "^18.3.1", "react-dom": "^18.3.1" },
          devDependencies: { vite: "^5.4.10", typescript: "^5.6.3" }
        },
        null,
        2
      )
    },
    {
      name: "index.html",
      content: `<!doctype html><html><body><div id="root"></div></body></html>`
    },
    {
      name: DEFAULT_ENTRY_POINT,
      content: `import { createRoot } from "react-dom/client";\ncreateRoot(document.getElementById("root")!).render(<h1>${title}</h1>);`
    }
  ];
}
