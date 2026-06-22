# Topic 1 - Project Management and Metadata

## Goal

Allow users to rename, describe, organize, and delete their projects after creation.

## Implementation work

### Backend

- Add `PATCH /projects/:id` for title, description, visibility, and optional tags.
- Add `DELETE /projects/:id`, initially as a soft delete using `deleted_at`.
- Validate title length, description length, visibility values, and tag limits.
- Verify that the project belongs to the requesting `X-Session-Id`.
- Update `updated_at` whenever metadata changes.
- Add database fields only when the UI will use them. Suggested first fields: `visibility`, `tags`, and `deleted_at`.

### Android

- Add `updateProject(...)` and `deleteProject(...)` to `ProjectRepository` and `VibeBuilderApi`.
- Add an edit action to Project Detail for title and description.
- Add a delete confirmation dialog.
- Add simple search and sorting on Home. Do not introduce folders until tags/search prove insufficient.
- Show saving, success, validation, and failure states.

### Tests

- Backend: ownership, validation, partial updates, soft deletion, and not-found cases.
- Android: edit success/failure, invalid input, deletion confirmation, and updated Home data.

## Acceptance criteria

- A renamed project shows the new title on Home and Project Detail after restarting the app.
- Invalid metadata never reaches persistence.
- A deleted project no longer appears in normal project lists.
- One session cannot update or delete another session's project.
