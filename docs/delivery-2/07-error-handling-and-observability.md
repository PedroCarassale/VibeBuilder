# Topic 7 - Error Handling and Observability

## Goal

Make failures diagnosable by developers and actionable for users.

## Implementation work

### Error contract

- Standardize API errors with `code`, `message`, `retryable`, `requestId`, and optional sanitized details.
- Map provider, validation, database, storage, timeout, and authorization failures to stable codes.
- Never send stack traces or secrets to Android.
- Add a request/correlation ID to logs and responses.

### Android

- Centralize API error-to-message mapping.
- Show retry only when `retryable` is true.
- Handle offline and timeout cases separately.
- Keep errors attached to the affected project/version instead of using only transient messages.

### Observability

- Extend telemetry with generation duration, validation duration, preview success rate, regeneration count, and error codes.
- Add structured logs with request, session hash, project, and version correlation.
- Add health/readiness endpoints for database and required services.
- Define alerts for elevated failure rate, latency, and preview unavailability.
- Document retention and avoid logging prompts or API keys by default.

### Tests

- Error mapping and secret redaction.
- Correlation ID propagation.
- Health endpoint behavior during dependency failure.
- Telemetry emitted once per operation, including idempotent replay.

## Acceptance criteria

- Every operational failure has a stable error code and correlation ID.
- Users receive a relevant next action.
- Developers can trace a failed generation across request, project, version, and provider stages.
