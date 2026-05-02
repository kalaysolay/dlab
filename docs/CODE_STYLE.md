# Damulab.kz Code Style

## Architecture

- Keep the MVP as a modular Spring Boot monolith.
- Put business rules in services/domain classes, not controllers or templates.
- Keep answer checking and score calculation on the backend.
- Keep UI server-rendered with Thymeleaf for MVP; use small page-specific JavaScript only where needed.
- Do not add email confirmation, password reset email, AI Content Factory, Arena, streaks, or LMS checkpoints unless a task explicitly asks for that scope.

## Packages

Use the package map from `03_ARCHITECTURE_VISION.md`:

- `kz.damulab.auth`
- `kz.damulab.users`
- `kz.damulab.parentlink`
- `kz.damulab.content`
- `kz.damulab.questions`
- `kz.damulab.lectures`
- `kz.damulab.testing`
- `kz.damulab.analytics`
- `kz.damulab.gamification`
- `kz.damulab.quiz`
- `kz.damulab.notifications`
- `kz.damulab.ai`
- `kz.damulab.admin`
- `kz.damulab.audit`
- `kz.damulab.common`

## Java

- Java 21.
- Constructor injection for required collaborators.
- DTO/form validation with Jakarta Validation annotations.
- Use transactions at service/use-case boundaries.
- Avoid leaking JPA entities directly to templates or REST responses once real domain models are added.

## Database

- Schema changes go through Flyway migrations.
- PostgreSQL is the source of truth.
- Tests use H2 only as a fast compatibility check; production behavior must be verified against PostgreSQL for DB-specific logic.

## UI

- Reuse Thymeleaf fragments for header, sidebar, alerts, modals, pagination, and repeated form blocks.
- Keep RU/KK text localizable through message bundles when it belongs to shared UI.
- Preserve mobile usability for key pages.
