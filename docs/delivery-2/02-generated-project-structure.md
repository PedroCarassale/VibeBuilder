# Topic 2 - Generated Project Structure

## Goal

Store each generated version as a defined web-app artifact rather than only a provider preview URL.

## Implementation work

### Artifact contract

Define a version artifact with:

- framework and template version,
- entry point,
- generated file manifest,
- dependency manifest,
- provider identifiers,
- validation result,
- preview/deployment reference.

Use React + Vite + TypeScript as the first supported project type. Keep dependencies minimal and reject unsupported binary files.

### Backend and storage

- Add artifact metadata tables linked to `project_versions`.
- Store large generated files in object storage, not directly in the relational database.
- Store a checksum and size for every file.
- Make artifacts immutable after a version reaches a final state.
- Save the generator/template version so old projects remain reproducible.
- Add an internal artifact service so provider-specific data does not leak into HTTP handlers.

### API

- Include an artifact summary in version-detail responses.
- Add a protected endpoint for exporting a version as an archive when export is implemented.
- Never return secrets, provider keys, or internal storage credentials.

### Tests

- Manifest serialization and validation.
- Artifact ownership and immutability.
- Missing, oversized, duplicate, and unsafe file paths.
- Database and object-storage failure consistency.

## Acceptance criteria

- Every successful version references a complete immutable artifact.
- A version can be validated or rebuilt without depending exclusively on a temporary provider URL.
- Failed artifact persistence never marks the version as successful.
