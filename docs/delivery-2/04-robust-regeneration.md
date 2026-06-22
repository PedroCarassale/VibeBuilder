# Topic 4 - Robust Regeneration

## Goal

Let users recover from failed generation without duplicate projects, lost history, or accidental repeated requests.

## Implementation work

### Backend

- Add `POST /projects/:id/versions/:versionId/regenerate`.
- Create a new version for every regeneration attempt; never overwrite the failed version.
- Record `source_version_id`, attempt number, failure code, and timestamps.
- Reuse the original prompt by default and allow an optional corrected prompt.
- Keep idempotency keys mandatory.
- Retry automatically only for explicitly retryable errors, with bounded exponential backoff and jitter.
- Do not automatically retry validation errors or invalid user input.
- Add generation states such as `queued`, `generating`, `validating`, `success`, `failed`, and `cancelled`.

### Android

- Show Regenerate on failed versions and generation failures.
- Display the current generation stage and prevent duplicate taps.
- Refresh status after app restart. Prefer polling first; background push can be added later.
- Preserve draft prompt text when a request fails.

### Tests

- Same idempotency key does not create multiple versions.
- Regeneration creates a new version in the same project.
- Failed attempts remain in history.
- Retryable and non-retryable errors follow different policies.
- Restarting the Android app recovers the current operation state.

## Acceptance criteria

- A user can regenerate any failed version without creating another project.
- Repeated network requests cannot create duplicate versions.
- The previous successful version remains the active version until regeneration succeeds.
