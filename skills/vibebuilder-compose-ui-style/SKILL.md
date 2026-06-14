---
name: vibebuilder-compose-ui-style
description: Apply VibeBuilder's Jetpack Compose design system when creating or refactoring Android UI. Use when adding new Compose screens, tabs, cards, forms, empty/loading/error states, prompt/chat views, preview/history views, or any mobile UI in the VibeBuilder app.
---

# VibeBuilder Compose UI Style

## Overview

Use the established VibeBuilder Compose design system instead of raw Material defaults. Keep UI changes visual and compositional unless the task explicitly asks for business logic changes.

## Start Here

Before creating or refactoring a view:

1. Inspect the nearest existing screen under `app/src/main/java/com/vibebuilder/app/ui/screens/`.
2. Reuse tokens from `app/src/main/java/com/vibebuilder/app/ui/theme/`:
   - `AppColors`
   - `AppTypography`
   - `AppSpacing`
   - `AppShapes`
3. Reuse components from `app/src/main/java/com/vibebuilder/app/ui/components/`:
   - `AppTopBar`
   - `SectionHeader`
   - `AppCard`
   - `AppTextField`
   - `PrimaryButton`
   - `SecondaryButton`
   - `GhostButton`
   - `PrimaryIconButton`
   - `StatusPill`
   - `StatusBanner`
   - `LoadingView`
   - `ErrorView`
   - `EmptyView` / `EmptyState`
4. Use `DesignSystemPreview.kt` as a quick visual reference for component composition.

## Visual Direction

Build new UI to feel like a modern AI/productivity SaaS product:

- Prefer neutral zinc/black/white surfaces over saturated colors.
- Use spacious layouts with `AppSpacing.screenHorizontal`, `screenVertical`, and `Arrangement.spacedBy(...)`.
- Use `AppCard` for grouped content, not raw `Card`.
- Use borders and zero or subtle elevation; avoid heavy shadows.
- Use `AppShapes.card`, `cardLarge`, `button`, `input`, and `pill`.
- Keep hierarchy clear: screen title, short subtitle, primary action, then content.
- Keep labels concise and support scanning on small screens.

## Screen Pattern

Use this shape for most new screens:

```kotlin
Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
        AppTopBar(
            title = stringResource(R.string.some_title),
            navigationIcon = { /* IconButton when needed */ },
            actions = { /* Icon buttons when needed */ }
        )
    }
) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(
                horizontal = AppSpacing.screenHorizontal,
                vertical = AppSpacing.screenVertical
            ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
    ) {
        SectionHeader(
            title = stringResource(R.string.some_header_title),
            subtitle = stringResource(R.string.some_header_subtitle)
        )
        // Screen content
    }
}
```

Use `LazyColumn` with the same horizontal/vertical content padding for lists.

## Forms

- Use `AppTextField`, not `OutlinedTextField`, in screens.
- Use `PrimaryButton` for the main submit action.
- Use `SecondaryButton` for secondary actions and tests.
- Use `StatusBanner(isError = true)` for submit errors.
- Keep validation messages in `supportingText` when tied to one field.
- Disable buttons during loading using existing state; do not add new state unless required.

## Lists And Cards

- Use `SectionHeader` before major list content.
- Use `AppCard(contentPadding = PaddingValues(AppSpacing.lg))` for list rows.
- Put metadata like version/status in `StatusPill`.
- Use `TextOverflow.Ellipsis` for project titles, descriptions, prompts, and URLs.
- Preserve item keys in `LazyColumn`.

## States

- Use `LoadingView` for full-screen loading.
- Use `ErrorView(message, onRetry)` for full-screen errors.
- Use `EmptyView(title, subtitle, actionLabel, onAction)` for empty screens.
- Use `StatusBanner` for inline feedback near the related action.

## Chat, Preview, And History

For prompt/chat surfaces:

- Keep the input anchored at the bottom with a subtle divider.
- Use `AppTextField` for prompt input and `PrimaryIconButton` for send.
- Use neutral assistant cards and dark user bubbles.
- Preserve optimistic-message behavior and auto-scroll effects.

For preview/history surfaces:

- Keep preview content visually framed in `AppCard(shape = AppShapes.cardLarge)`.
- Put version/status information in `StatusPill`.
- Use `PrimaryButton` for "open preview" style actions.
- Use `SecondaryButton` for retry actions.

## Guardrails

- Do not change ViewModel APIs, repository calls, navigation routes, or business logic for styling tasks.
- Do not add external libraries for visual polish unless explicitly requested.
- Do not introduce one-off colors, dimensions, or shapes in screen files when a token exists.
- Avoid direct `Button`, `OutlinedButton`, `TextButton`, `OutlinedTextField`, `Card`, or `TopAppBar` in screens; wrap through the design-system components unless there is a clear exception.
- Keep new strings in `app/src/main/res/values/strings.xml`.
- Add Compose previews only when they are simple and do not require ViewModels or network state.

## Verification

After UI changes, run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
```

If Gradle needs network to download dependencies, request approval rather than skipping validation.
