# Topic 3 - Generated-Output Validation

## Goal

Detect broken or unsafe generated web apps before presenting them as successful.

## Implementation work

### Validation pipeline

Run these checks after generation and before setting a version to `success`:

1. Validate the artifact manifest and safe relative file paths.
2. Require core files such as `package.json`, `index.html`, and the configured entry point.
3. Parse `package.json` and enforce an allowlist or denylist policy for dependencies.
4. Install dependencies in an isolated environment with time, CPU, memory, and network limits.
5. Run TypeScript checks and a production build.
6. Verify that build output exists and respects size limits.
7. Perform a preview health check and record the result.

### Data and API

- Add a `validation_status` and structured validation report to each version.
- Keep user-facing messages separate from internal diagnostics.
- Use stable error codes such as `INVALID_MANIFEST`, `BUILD_FAILED`, and `PREVIEW_UNHEALTHY`.
- Mark invalid output as `failed`; do not update `current_version_id`.

### Android

- Show a short actionable failure message.
- Offer regeneration for retryable generation failures.
- Avoid exposing raw compiler logs by default; an optional details section may show sanitized diagnostics.

### Tests

- Valid minimal app.
- Missing entry point, invalid JSON, prohibited dependency, path traversal, timeout, and build failure.
- Verification that a failed validation does not replace the latest successful version.

## Acceptance criteria

- A version is only marked successful after all required validation stages pass.
- Validation failures remain visible in history with a clear reason.
- Validation runs in an isolated, resource-limited environment.
