# Delivery 2 - Mock or Supabase Database Architecture

## Objective

Build a capability-aware generation workflow that detects when a generated app needs persistent data and asks the user to choose:

- **Mock data:** a localStorage repository with realistic seed data.
- **Real data:** a remembered Supabase connection with generated tables and working CRUD.

Delivery 2 supports Supabase database CRUD only. Authentication, Storage, Realtime, and other providers are out of scope, but integration code must use provider interfaces so they can be added later.

Saved Supabase connections belong to the current device session and are reusable across projects. If exactly one valid connection exists, choosing Real binds it automatically. A project started with mock data can later convert to Supabase by creating a new version.

## Required Architecture

### Secure device identity

The existing UUID-only `X-Session-Id` must not protect stored database credentials by itself.

- Add device-session registration with a random bearer token.
- Store only the token hash on the backend.
- Store the token in Android using Keystore-backed storage.
- Add a one-time upgrade path for existing installations while preserving their `session_id` and projects.
- Require the bearer token on all integration credential operations.

### Persistent models

Add these backend entities:

#### `integration_connections`

- `id`
- owner session ID
- provider (`supabase` initially)
- display name
- status (`valid`, `invalid`, `revoked`)
- public configuration JSON
- encrypted secret payload
- masked credential hints
- supported capabilities
- last validation timestamp
- created and updated timestamps

#### `project_data_configs`

- project ID
- mode (`mock` or `real`)
- optional connection ID
- unique table prefix
- schema revision
- updated timestamp

#### `generation_requests`

- project ID
- prompt
- detected capabilities
- selected data mode
- optional connection ID
- state
- resulting version ID
- structured error
- idempotency key
- created and updated timestamps

Generation states:

```text
analyzing
  -> awaiting_data_mode
  -> awaiting_connection
  -> generating
  -> configuring_database
  -> validating
  -> success | failed
```

#### `project_provider_contexts`

Persist the v0 project, chat, and latest remote version identifiers as first-class fields instead of hiding them in `provider_meta`.

#### `database_manifests`

- project and version IDs
- validated manifest JSON
- checksum
- schema revision
- migration state
- applied timestamp
- connection ID

Versions remain immutable. Update `current_version_id` only after generation, database configuration, CRUD validation, and preview validation succeed.

## Credential Vault

The Supabase wizard collects:

- Connection display name.
- Supabase project URL.
- Publishable or legacy anonymous key.
- PostgreSQL pooler connection string for schema setup.

Rules:

- Encrypt the PostgreSQL connection string with AES-256-GCM using a dedicated `INTEGRATION_KEYSTORE_SECRET`.
- Return only connection summaries and masked hints to Android.
- Never place the PostgreSQL connection string in prompts, logs, telemetry, provider metadata, generated files, or v0 environment variables.
- Send only browser-safe Supabase URL and publishable/anonymous keys to v0.
- Validate credentials before saving.
- Revalidate an invalid connection or one whose validation is stale before applying migrations.
- Restrict connection targets to recognized Supabase endpoints to prevent server-side request forgery.
- Deleting a connection removes its vault credentials and attempts to remove its v0 environment variables. It must never delete the user's Supabase project or data.

## Database Requirement Detection

Create an `IntegrationRequirementDetector` interface so detection is independent of v0.

- Analyze the initial prompt before normal generation.
- Return strict data containing `requiresDatabase`, capabilities, expected entities, and a user-facing reason.
- Implement the initial detector with a short v0 planning request and strict JSON parsing.
- Fall back to deterministic database-related rules when the response is invalid.
- Add a project Data setting so users can correct a missed detection.
- Once a project has a data mode, all follow-up prompts reuse it without asking again unless its real connection is invalid or removed.

## Safe Schema Generation

Never execute arbitrary SQL produced by v0.

Require generated versions to contain:

- `vibebuilder.database.json`: constrained tables, columns, relations, and indexes.
- `vibebuilder.seed.json`: optional bounded sample records.

The backend must:

- Strictly validate both manifests.
- Support an allowlist of column types and constraints.
- Reject embedded SQL and unsafe identifiers.
- Apply a unique prefix such as `vb_<project-id>_` to every table.
- Compile trusted SQL from the validated manifest.
- Allow only additive changes: create tables, columns, indexes, and policies.
- Reject drops, renames, column narrowing, and destructive type changes.
- Apply migrations transactionally.
- Enable RLS and generate anonymous CRUD policies only for explicitly public prototype tables.
- Insert validated seed fixtures.
- Run an HTTP CRUD smoke test using the public key before marking the version successful.

Without Auth, real database apps contain shared public prototype data. The Android UI must warn users not to store sensitive data.

## v0 Orchestration

For each VibeBuilder project:

- Create or recover a corresponding v0 project and chat.
- Persist remote identifiers in `project_provider_contexts`.
- Assign the chat to the v0 project.
- Configure public Supabase environment variables on the v0 project.
- Add project instructions defining the repository contract, manifest format, table prefix, and secret restrictions.
- Download generated version files and safely extract them with path and size limits.
- Validate the generated artifact and database manifests.
- Never rely on v0's interactive Supabase connection screen.
- Preserve the same v0 chat for follow-up prompts.

Provide public environment aliases required by supported generated frameworks. Never expose the PostgreSQL connection string or future privileged keys.

Mock mode and real mode must implement the same generated repository interface:

- Mock implementation uses localStorage and seed fixtures.
- Real implementation uses the Supabase client and prefixed tables.

This interface is required for mock-to-real conversion without rewriting unrelated UI.

## Backend API

Add:

```text
POST   /sessions
POST   /sessions/upgrade

GET    /integrations/connections?provider=supabase
POST   /integrations/supabase/connections
PATCH  /integrations/connections/:id
POST   /integrations/connections/:id/test
DELETE /integrations/connections/:id

GET    /projects/:id/data-config
POST   /projects/:id/generation-requests
GET    /generation-requests/:id
POST   /generation-requests/:id/data-mode
POST   /generation-requests/:id/connection
POST   /projects/:id/data-config/upgrade
```

Return a typed generation request containing:

- request ID and state,
- required capabilities,
- selected data mode,
- safe connection summary,
- resulting version ID,
- structured error code, message, and retryability.

Never return credential values. Keep the existing prompt endpoint temporarily as a compatibility wrapper for prompts that require no user action.

All mutations must be idempotent and verify project/connection ownership.

## Android Changes

### Prompt workflow

Change prompt submission from always returning a version to returning a state-based result.

- Show a Mock/Real bottom sheet for `awaiting_data_mode`.
- Mock proceeds without a connection.
- Real automatically binds the only valid saved Supabase connection.
- Show a picker when multiple valid connections exist.
- Open the connection wizard when none exist.
- Preserve the pending prompt across navigation and process recreation.
- Resume a pending generation request when Project Detail reopens.
- Block duplicate submissions while the request is active.

### Supabase connection wizard

Add an Integrations section alongside v0 settings.

- Enter connection name, project URL, publishable key, and PostgreSQL pooler URL.
- Test before Save.
- Display validation progress and field-specific errors.
- Never display a saved secret again.
- List, retest, update, and remove saved connections.
- Warn when removing a connection affects existing projects.

### Project data controls

Project Detail must show:

- Current mode: Mock or Supabase.
- Bound connection name and health.
- Public prototype data warning.
- Change or reconnect action when invalid.
- **Connect real database** action for mock projects.

### Mock-to-real conversion

1. Select or create a Supabase connection.
2. Reuse the latest validated database manifest and seed fixtures.
3. Apply the compiled schema.
4. Send a follow-up instruction to the existing v0 chat to replace the mock repository adapter.
5. Validate the new artifact and CRUD behavior.
6. Create a new successful version while preserving mock history.

Do not migrate runtime localStorage data. Import only validated seed fixtures.

### Visible generation stages

- Analyzing requirements
- Waiting for data choice
- Waiting for connection
- Generating app
- Configuring database
- Validating CRUD
- Completed
- Failed with retry or reconnect action

## Failure Behavior

- A failed requirement analysis falls back to deterministic detection.
- A failed connection test does not save or bind the connection.
- A failed migration rolls back the transaction.
- A failed migration or CRUD test marks the generation request/version failed and leaves `current_version_id` unchanged.
- An invalid or deleted saved connection changes bound projects to a disconnected state.
- Removing a connection must not delete remote database data.
- Credential rotation marks affected generated apps as needing a new version.
- Retrying a request must not create duplicate versions, connections, or migrations.

## Testing

### Backend

- Device-session creation, upgrade, authentication, and cross-session isolation.
- Credential encryption, masking, rotation, and deletion.
- Supabase validation success, invalid keys, unreachable databases, and blocked hosts.
- Requirement detection and invalid analyzer fallback.
- Automatic reuse of one connection and selection requirement for multiple connections.
- Manifest rejection for SQL, unsafe names, excessive sizes, and unsupported types.
- Additive SQL compilation and destructive-change rejection.
- Transaction rollback on migration or seed failure.
- RLS and anonymous CRUD smoke tests.
- v0 project assignment, environment injection, artifact download, and identifier persistence.
- Idempotent generation requests and recovery after retry.
- Current version remains unchanged on any failed stage.

### Android

- Mock/Real decision flow.
- Automatic saved-connection reuse.
- Multiple-connection picker.
- Connection wizard validation and secret masking.
- Pending prompt restoration.
- Generation-stage rendering and actionable errors.
- Mock-to-real upgrade.
- Invalid or removed connection recovery.
- Public-data warning visibility.

### End-to-end

1. Generate a frontend-only app without an integration prompt.
2. Generate a database app using mock data.
3. Connect Supabase and generate a working CRUD app.
4. Create another database project and reuse the connection without re-entering credentials.
5. Add a field through a follow-up prompt and apply an additive migration.
6. Convert a mock project to real Supabase.
7. Reject a destructive schema request without changing existing data.
8. Remove a connection and verify affected projects request reconnection.
9. Restart Android during the decision flow and resume it.

## Rollout

- Protect the feature with `SUPABASE_INTEGRATIONS_ENABLED`.
- Existing projects remain unchanged until a database requirement is detected or the user opens Data settings.
- Do not log prompts, keys, connection strings, or decrypted configuration.
- Add telemetry for mode selection, connection validation, migration duration, CRUD validation, conversion, and sanitized failure codes.
- Preserve existing uncommitted project-management changes and integrate with them instead of replacing them.

## Definition of Done

- A user can choose Mock or Real when a prompt requires a database.
- Mock mode generates a functional localStorage CRUD application.
- Real mode can connect, remember, and reuse a Supabase project.
- VibeBuilder safely creates prefixed tables and validates browser CRUD.
- Follow-up prompts can add non-destructive schema changes.
- A mock project can convert to Supabase as a new version.
- Credentials never reach prompts, generated source, telemetry, or Android responses after initial submission.
- Failed database operations never replace the last successful project version.
- Backend, Android, and end-to-end test scenarios pass from a clean checkout.

## Fixed Product Decisions

- Identity remains device-session based for Delivery 2.
- Supabase projects are supplied by users; VibeBuilder does not provision them.
- Supabase database CRUD is the only real integration capability in scope.
- Privileged setup credentials are encrypted and remembered.
- One valid saved connection is reused automatically.
- Real data is public shared prototype data until Auth is implemented.
- Schema changes are additive only.
- Mock-to-real conversion creates a new version and imports seed fixtures only.
