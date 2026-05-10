import test from "node:test";
import assert from "node:assert/strict";
import {
  resolveGenerationProvider,
  resolveGenerationTimeoutMs
} from "../src/generation-provider.js";

function silentLogger() {
  return { warn: () => {}, info: () => {}, log: () => {} };
}

test("resolveGenerationProvider cae a mock-v0 si V0_API_KEY no esta definida", () => {
  const provider = resolveGenerationProvider({
    env: {},
    logger: silentLogger()
  });

  assert.equal(provider.name, "mock-v0");
});

test("resolveGenerationProvider cae a mock-v0 si V0_API_KEY esta vacia", () => {
  const provider = resolveGenerationProvider({
    env: { V0_API_KEY: "   " },
    logger: silentLogger()
  });

  assert.equal(provider.name, "mock-v0");
});

test("resolveGenerationProvider emite warning cuando cae al mock", () => {
  const warnings = [];
  const logger = {
    warn: (message) => warnings.push(message)
  };

  resolveGenerationProvider({ env: {}, logger });

  assert.equal(warnings.length, 1);
  assert.match(warnings[0], /V0_API_KEY/);
  assert.match(warnings[0], /mock/i);
});

test("resolveGenerationProvider devuelve proveedor v0 cuando V0_API_KEY esta presente", () => {
  const provider = resolveGenerationProvider({
    env: { V0_API_KEY: "test-key" },
    logger: silentLogger()
  });

  assert.equal(provider.name, "v0");
});

test("resolveGenerationTimeoutMs prioriza V0_TIMEOUT_MS, luego GENERATION_TIMEOUT_MS, luego fallback", () => {
  assert.equal(
    resolveGenerationTimeoutMs({ env: {}, fallbackMs: 5000 }),
    5000
  );
  assert.equal(
    resolveGenerationTimeoutMs({
      env: { GENERATION_TIMEOUT_MS: "12000" },
      fallbackMs: 5000
    }),
    12000
  );
  assert.equal(
    resolveGenerationTimeoutMs({
      env: { V0_TIMEOUT_MS: "9000", GENERATION_TIMEOUT_MS: "12000" },
      fallbackMs: 5000
    }),
    9000
  );
  assert.equal(
    resolveGenerationTimeoutMs({
      env: { V0_TIMEOUT_MS: "no-numero" },
      fallbackMs: 5000
    }),
    5000
  );
  assert.equal(
    resolveGenerationTimeoutMs({
      env: { V0_TIMEOUT_MS: "-100" },
      fallbackMs: 5000
    }),
    5000
  );
});
