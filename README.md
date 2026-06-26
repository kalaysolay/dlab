# Damulab.kz

Spring Boot MVP foundation for Damulab.kz.

## Stack

- Java 21
- Spring Boot 3.x
- Spring MVC + Thymeleaf
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL

## Local Run

Start PostgreSQL locally, then run the app with the Gradle wrapper:

```powershell
.\gradlew.bat bootRun
```

Default demo users for stage 0:

- `admin@damulab.kz` / `password`
- `student@damulab.kz` / `password`
- `parent@damulab.kz` / `password`

## Test

```powershell
.\gradlew.bat test
```

Tests use the `test` profile and H2 in PostgreSQL compatibility mode.

For local browser QA without PostgreSQL, run the app with the test profile:

```powershell
$env:SPRING_PROFILES_ACTIVE="test"
$env:SERVER_PORT="18080"
.\gradlew.bat bootRun
```

## Stage Documentation

- `docs/DEPLOY_HTTPS.md` - production HTTPS (Let's Encrypt), nginx и проверка PWA.
- `docs/PARENT_LINK_API.md` - stage 2 parent-child linking endpoints and rules.
- `docs/PWA_BASELINE.md` - minimal PWA assets and verification scope.
- `docs/CONTENT_GRAPH_API.md` - stage 3 content graph endpoints and admin UI.
- `docs/QUESTION_BANK_API.md` - stage 4 question bank endpoints, DTO rules, validation and admin UI.
- `docs/TESTING_HUB_API.md` - stage 5 test session endpoints, answer checking, and student UI.
- `docs/ANALYTICS_API.md` - stage 6 mastery, knowledge map, timeline, last errors and parent visibility.
- `docs/STUDENT_ENGAGEMENT.md` - stage 7 student dashboard, streak, achievements and notification settings.
- `docs/LECTURES_LMS.md` - stage 8 lecture CRUD, publication rules, attachments, checkpoints and student lecture view.

## MVP Scope Guardrails

- Email confirmation is not part of MVP.
- No separate SPA for MVP.
- Answer checking and business rules must stay on the backend.
- AI Content Factory, Arena, streak/achievements, and LMS checkpoints are post-MVP unless a task explicitly asks for them.
