# Lectures and LMS API

Status: stage 8 vertical slice with remainder QA completed.

## Scope

The MVP slice adds methodist/admin lecture management and a mobile-friendly student lecture reader. It does not introduce a separate SPA and does not make full interactive LMS checkpoint attempts a blocker for MVP.

Implemented:

- `Lecture`, `LectureVersion`, `LectureAttachment`, `LectureCheckpoint`.
- Admin REST endpoints and server-rendered pages.
- Draft save with partial localization.
- Publication validation: topic, RU title/content and KK title/content are required.
- Control modes: `NONE`, `AUTO`, `MANUAL`.
- `AUTO` checkpoint mode selects published question versions from the same topic.
- Attachments are link metadata for this slice; binary upload/storage remains a later file-storage task.
- Textarea fallback stores content as sanitized HTML: user HTML is escaped and line breaks become safe `<br>` tags.
- Student pages list published lectures and show the lecture body, attachments and checkpoint question prompts without exposing answer keys.
- Visual/mobile QA for the lecture routes was run on desktop, 390px and 360px widths. Page-level horizontal overflow is absent; admin tables use internal `.table-wrap` scrolling on narrow screens.

## Data Model

- `lectures`
  - logical lecture record;
  - `status`: `DRAFT`, `PUBLISHED`, `ARCHIVED`;
  - points to `current_version_id`.
- `lecture_versions`
  - versioned content and metadata;
  - optional `topic_id` while draft;
  - `title_ru`, `title_kk`, `content_ru_html`, `content_kk_html`;
  - `control_mode`: `NONE`, `AUTO`, `MANUAL`.
- `lecture_attachments`
  - metadata links: title, URL, media type, order.
- `lecture_checkpoints`
  - links a lecture version to published `question_versions`.

Topic deletion is blocked with `topic_has_lectures` when any lecture version references the topic.

## Admin REST Endpoints

- `GET /api/admin/lectures`
  - query params: `topicId`, `status`, `query`;
  - returns lecture list with current version, attachment count and checkpoint count.
- `GET /api/admin/lectures/{id}`
- `POST /api/admin/lectures`
- `PATCH /api/admin/lectures/{id}`
- `POST /api/admin/lectures/{id}/publish`
- `POST /api/admin/lectures/{id}/archive`

All endpoints require `ROLE_ADMIN` in the current MVP security model.

## Request DTO

```json
{
  "topicId": 1,
  "titleRu": "Проценты в повседневной жизни",
  "titleKk": "Күнделікті өмірдегі пайыздар",
  "contentRu": "Процент - это сотая часть числа.\nP = (a / b) * 100%",
  "contentKk": "Пайыз - санның жүзден бір бөлігі.",
  "source": "Ручной ввод",
  "controlMode": "AUTO",
  "autoCheckpointCount": 3,
  "checkpointQuestionVersionIds": [],
  "attachments": [
    {
      "title": "percent_examples.pdf",
      "url": "/files/percent_examples.pdf",
      "mediaType": "pdf"
    }
  ]
}
```

## Validation Codes

- `lecture_title_required`
- `lecture_content_required`
- `lecture_topic_required`
- `lecture_bilingual_title_required`
- `lecture_bilingual_content_required`
- `lecture_auto_checkpoint_count_required`
- `lecture_auto_checkpoints_not_found`
- `lecture_manual_checkpoints_required`
- `checkpoint_question_not_published`
- `checkpoint_topic_mismatch`
- `lecture_attachment_required`
- `lecture_attachment_url_invalid`

## Server-Rendered Pages

- `GET /admin/lectures`
- `GET /admin/lectures/new`
- `GET /admin/lectures/{id}/edit`
- `GET /admin/lectures/{id}/preview`
- `GET /student/lectures`
- `GET /student/lectures/{id}`

## Stage 8 Remainder QA

Date: 2026-04-27.

Checked with local Chrome/Playwright against `test` profile on `http://localhost:18083`:

- `/admin/lectures`
- `/admin/lectures/new`
- `/admin/lectures/{id}/edit`
- `/admin/lectures/{id}/preview`
- `/student/lectures`
- `/student/lectures/{id}`

Viewports:

- desktop `1366x900`
- mobile `390x844`
- mobile `360x800`

Result:

- `body`/document horizontal overflow is `0` for all checked routes and widths.
- Forms, RU/KK tabs, action buttons, lecture reader and student list remain readable on 360-390px.
- `/admin/lectures` keeps the wide data table in an internal horizontal scroll region instead of expanding the page.
- QA artifacts are written under `.run-logs/stage8-lecture-qa/` during local runs.

Fixes made from QA:

- Direct `/admin/lectures/new` now defaults to the math/grade-4 topic set instead of the first subject without topics.
- Empty spare attachment rows are ignored even when the HTML select posts its default media type; partially filled rows still fail validation.
- Edit forms copy attachment/checkpoint lists into mutable lists before adding spare rows.
- CSS grid panels no longer expand from wide tables on mobile.
- Lecture textarea height is applied correctly.

## Rich Editor Decision

The stage 8 remainder intentionally keeps the safe textarea fallback and does not add a CDN dependency. A local Quill/KaTeX bundle is deferred to a separate asset task because it needs pinned local files, license/provenance review, toolbar configuration, formula rendering checks and an explicit rich-text sanitation/rendering contract. Until that task is done, lecture text is escaped on the server and line breaks are rendered as safe `<br>` tags.

## Remaining Gaps

- Separate asset task: local Quill/KaTeX bundle or another controlled rich editor, with no production CDN dependency.
- Binary upload/object storage for lecture files.
- Full student checkpoint attempt flow with answer submission and server-side evaluation.
- Offline authenticated lecture cache/PWA behavior.
- Question picker UI for manual checkpoint selection.

## Stabilization Update (2026-05-01)

- Fixed lecture editor submit blocker for `NONE`/`MANUAL` control modes:
  - `autoCheckpointCount` is now disabled outside `AUTO`, so browser native validation does not block draft submit.
- Fixed multipart upload binding for lecture attachments in server-rendered admin forms:
  - file inputs are posted as `attachmentFiles[index]`;
  - controller now resolves both indexed and legacy unindexed multipart payloads safely.
- Fixed attachment metadata consistency in lecture update path:
  - when `storageKey` is present, response/file URL is normalized to `/files/lecture-attachments/{storageKey}`;
  - this prevents `metadata/storageKey/url` drift after edit/update operations.
- Added coverage for attachment replacement lifecycle:
  - update with a new file deletes the previous storage file;
  - new file URL is immediately downloadable.
- Added coverage for MANUAL checkpoint picker submit path:
  - `checkpointQuestionVersionIds` posted from the form persist and are applied on publish.
- Hardened lecture topic submit path in admin form:
  - added `topicIdMirror` fallback and client-side sync to prevent `topicId` loss in multipart submits;
  - topic selector is no longer temporarily disabled during async topic reload to avoid browser dropping the field.
- Added manual E2E scenario script for stage stabilization:
  - `scripts/smoke/manual-lecture-attachment-checkpoint-e2e.js` verifies create -> upload file -> draft -> edit -> publish -> student open/download attachment;
  - the same flow verifies MANUAL checkpoint picker search/add/remove and persistence after submit/publish.
