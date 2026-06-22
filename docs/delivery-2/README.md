# Delivery 2 - Control and Quality

This folder turns the Delivery 2 goals into implementation topics for the current Android and Node.js codebase.

## Recommended order

1. [Project management and metadata](./01-project-management-and-metadata.md)
2. [Generated project structure](./02-generated-project-structure.md)
3. [Generated-output validation](./03-generated-output-validation.md)
4. [Robust regeneration](./04-robust-regeneration.md)
5. [Advanced preview](./05-advanced-preview.md)
6. [Improved version history](./06-improved-version-history.md)
7. [Error handling and observability](./07-error-handling-and-observability.md)
8. [Testing and release readiness](./08-testing-and-release-readiness.md)
9. [Mock or Supabase database architecture](./09-mock-or-supabase-architecture.md)

The order is intentional. Regeneration, preview, and history should use a stable version/artifact model rather than adding temporary behavior around `previewUrl` only.

## Delivery 2 definition of done

Delivery 2 is complete when a user can organize a project, safely regenerate it after a failure, validate and preview generated output, inspect previous versions, and recover from common errors without losing project history. Backend and Android automated tests must pass from a clean checkout.
