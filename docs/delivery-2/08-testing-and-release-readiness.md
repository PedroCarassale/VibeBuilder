# Topic 8 - Testing and Release Readiness

## Goal

Make Delivery 2 reproducible and verifiable from a clean checkout.

## Environment baseline

- Document Node.js and JDK versions. Android currently targets Java 17.
- Use `npm ci` for backend dependencies.
- Keep the Gradle wrapper committed and verify it from CI.
- Provide sanitized test environment variables and an isolated database.

## Automated test layers

### Backend

- Unit tests for validation, retry policies, artifact services, and error mapping.
- Integration tests for every project/version endpoint and ownership rule.
- Persistence tests against local SQLite and a Turso-compatible environment.
- Contract tests for generation-provider responses.

### Android

- ViewModel and repository tests for metadata editing, regeneration, preview resolution, and history selection.
- Compose UI tests for loading, empty, success, validation, and error states.
- WebView integration tests for navigation and preview failures where emulator support is required.

### End-to-end

- Create project, generate, validate, preview, iterate, fail, regenerate, inspect history, restore, rename, and reopen the app.
- Include network interruption and provider timeout scenarios.
- Verify that no retry creates duplicate projects or versions.

## Continuous integration

- Run backend tests and Android unit tests on every pull request.
- Run lint/static analysis and build the debug APK.
- Run emulator E2E tests on the release branch or scheduled workflow.
- Block merging when required checks fail.

## Release checklist

- All automated checks pass from a clean checkout.
- Database migrations are tested forward and rollback/recovery procedures are documented.
- No secrets or local databases are tracked.
- Delivery 2 acceptance flow passes against the deployed backend.
- Known limitations and recovery steps are documented.

## Acceptance criteria

- A new developer or CI runner can install dependencies and run all tests using documented commands.
- Required checks are green before release.
- The complete Delivery 2 user flow is repeatable without manual database repair.
