# Topic 6 - Improved Version History

## Goal

Turn version history from a read-only list into a usable project-control surface.

## Implementation work

### Backend

- Add version-detail retrieval with prompt, status, failure reason, artifact summary, and parent/source version.
- Support pagination with a stable cursor rather than returning an unbounded list.
- Preserve immutable version records.
- Add `POST /projects/:id/versions/:versionId/restore`; restoration should create a new version instead of mutating history.
- Add a lightweight comparison summary after the artifact model exists.

### Android

- Make each history card selectable.
- Add actions to preview, regenerate, and restore eligible versions.
- Show generation/validation failure reasons.
- Clearly identify the active version, latest successful version, and failed attempts.
- Add pagination/loading and retry states.

### Tests

- Correct ordering and cursor pagination.
- Previewing an old version does not change the active version.
- Restore creates a new version and preserves the original.
- Failed and successful versions remain distinguishable.

## Acceptance criteria

- Users can inspect and preview a previous version.
- Users can restore a previous successful version without deleting newer history.
- Large histories load incrementally and remain correctly ordered.
