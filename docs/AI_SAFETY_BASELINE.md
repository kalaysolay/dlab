# AI Safety Baseline

Status: mandatory baseline for AI-related engineering work.
Date: 2026-04-27.

This document fixes the default AI safety and privacy boundaries for Damulab.kz before stage 9 AI Content Factory work.

Legal context: the Kazakhstan personal data law defines personal data broadly as data related to an identified or identifiable subject. Official source: https://adilet.zan.kz/rus/docs/Z1300000094

## Core Decision

Damulab stores personal data only in the Damulab database. External AI providers must not receive personal data or direct identifiers.

AI may receive minimized educational context only when it is needed for generation or analysis. Test results linked to a concrete student are treated as personal data inside Damulab and must be stripped of identifiers before any AI call.

## Never Send To AI

- Student, parent or admin full name.
- Email, phone, login, password hash or session data.
- Internal `user_id`, `student_id`, `parent_id`, link codes or parent-child relationship data.
- Raw exports that let a provider reconstruct a child's profile.
- Free-form notes that may contain personal or family information.
- Secrets, API keys, cookies or CSRF tokens.

## Allowed AI Payload Shape

Allowed only after minimization:

- Subject, grade and language.
- Topic, atomic skill, difficulty and question type.
- Question text and answer variants prepared by a methodist.
- An anonymized error pattern, for example: "student selected option B instead of C on percent-of-number skill".
- Aggregate performance bands, for example: "low mastery on fractions", without identity.
- Methodist prompt text that does not include personal data.

When uncertain, remove the field.

## Product Rules

- AI output is draft content only.
- AI-generated questions, explanations or mini-lectures are never published automatically.
- A methodist must review, edit, approve or reject AI output before it reaches the production content bank.
- Correct answers and server-side answer checking stay on the backend.
- AI is not a blocker for the MVP learning loop: content -> test -> result -> analytics -> parent.

## Engineering Rules

- Start stage 9 with `AiProvider` and a stub/mock provider; real providers are configuration-driven.
- Provider credentials live only on the server and never in templates, JavaScript or browser payloads.
- Real provider calls must be behind config/feature flags.
- Do not log raw prompts/responses if they can contain personal data. Use redacted structured logs with job id, provider, status and error code.
- Store AI jobs and batches with enough audit metadata to support human review, but avoid storing unnecessary provider payloads.
- Prompt templates must be owned by backend code/config, not hardcoded into browser scripts.
- Add tests that prove AI output is not published without human approval.

## Stage 9 First Slice Checklist

- `AiProvider` interface exists.
- `StubAiProvider` works without network calls.
- `AiGenerationJob` records status and audit metadata.
- Admin UI can create a generation request and see draft results.
- Methodist approve/edit/delete is explicit.
- No personal identifiers are present in outbound provider DTOs.
- Tests cover provider failure, retry/error state and no-autopublish behavior.

