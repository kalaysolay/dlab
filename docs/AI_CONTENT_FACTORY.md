# AI Content Factory

Status: Stage 9 implementation closed for current scope.
Date: 2026-04-27.

This document fixes the implemented Stage 9 boundaries. The default local/test mode performs no external network calls. Real providers are available only through server-side config and feature flags.

## Implemented Scope

- `AiProvider` interface with `StubAiProvider`.
- Provider router with OpenAI as primary direction and DeepSeek as fallback direction.
- OpenAI and DeepSeek server-side adapters behind `damulab.ai.real-providers-enabled`.
- Backend prompt builder and schema validation for generated question drafts.
- `AiGenerationJob` with status, retry count, provider/model metadata and redacted outbound request payload.
- Review batch storage through `AiGeneratedQuestionBatch` and `AiGeneratedQuestionItem`.
- Admin UI at `/admin/questions/ai-generate`.
- Human-in-the-loop actions:
  - approve AI item into the question bank as `needs_review`;
  - edit AI item text/source before approval;
  - delete AI item from the review batch.
- Tests for job creation, provider failure/retry, no-autopublish, outbound DTO redaction, provider feature flag and draft schema validation.

## Provider Decision

Current provider direction:

- Primary: OpenAI.
- Fallback: DeepSeek, mainly for lower-cost generation when quality and policy allow it.

Both real providers must be implemented as server-side `AiProvider` adapters behind config and feature flags. The current implementation remains on `StubAiProvider` and does not make external calls.

Configuration defaults:

- `AI_PROVIDER=stub`
- `AI_FALLBACK_PROVIDER=deepseek`
- `AI_REAL_PROVIDERS_ENABLED=false`
- `OPENAI_MODEL=gpt-5.2`
- `DEEPSEEK_MODEL=deepseek-chat`

Required before real calls:

- server-side `OPENAI_API_KEY` and/or `DEEPSEEK_API_KEY`;
- `AI_REAL_PROVIDERS_ENABLED=true`;
- confirmed data-processing/legal terms for the selected provider.

## DTO Boundary

Outbound provider DTO is `AiQuestionGenerationRequest`.

Allowed fields:

- subject title RU/KK;
- grade number;
- topic title RU/KK;
- atomic skill title RU/KK, if selected;
- question type;
- difficulty;
- count;
- language mode;
- sanitized methodist instruction.

Forbidden fields:

- `user_id`, `student_id`, `parent_id`, raw internal IDs or link codes;
- email, phone, login, full name;
- parent-child relationship data;
- raw student results tied to a concrete student;
- provider credentials, CSRF tokens, cookies or secrets.

The first slice redacts common direct identifiers in free-form instruction before storing or sending the outbound DTO. When uncertain, remove the field instead of forwarding it.

## Persistence

Flyway migration: `V11__ai_content_factory.sql`.

Tables:

- `ai_generation_jobs`;
- `ai_generated_question_batches`;
- `ai_generated_question_items`.

Generated items store enough draft content and quality metadata for review, but do not publish anything automatically.

## Remaining Non-Goals

- No real external calls unless explicitly enabled by server-side config.
- No AI explanations for individual student results.
- No raw prompt/response logging.
- No automatic publication into `published`.
- No browser-side provider credentials or prompt construction.
