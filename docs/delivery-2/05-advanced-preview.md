# Topic 5 - Advanced Preview

## Goal

Make preview reliable for current and historical versions on a mobile device.

## Implementation work

### Correctness first

- Render the resolved fallback URL in the WebView, not only `currentVersion.previewUrl`.
- Allow previewing a selected historical version through the existing version preview API.
- Distinguish expired, unavailable, not-ready, offline, HTTP, TLS, and rendering errors.
- Add a refresh action and external-browser fallback.

### WebView controls

- Add back, forward, refresh, and open externally.
- Show the version number and preview status.
- Keep navigation inside the generated preview's trusted hosts; open unrelated links externally.
- Disable unsafe file access and configure WebView security explicitly.
- Preserve preview state across tab changes where practical.

### Backend

- Health-check preview URLs and refresh expired URLs when the provider supports it.
- Record preview latency and failure reason without storing sensitive URL parameters.
- Define preview expiration behavior in the API contract.

### Tests

- Current and historical version preview.
- Resolved fallback URL rendering.
- Expired URL, offline mode, TLS failure, HTTP error, and retry.
- WebView navigation policy.

## Acceptance criteria

- Any successful version with an available preview can be rendered from History.
- Preview failures provide a specific recovery action.
- A resolved fallback URL behaves the same as a URL included in the version response.
