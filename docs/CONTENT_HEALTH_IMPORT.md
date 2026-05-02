# Stage 12: Content health dashboard and import

Status: Stage 12 engineering scope completed on 29 April 2026.

## Scope

Stage 12 gives admins a controlled quality workflow for the question bank and a batch import path. It stays inside the existing Spring MVC + Thymeleaf admin UI, reuses server-side question validation and never exposes `answer_key_json` in public health DTOs.

Implemented:

- `GET /api/admin/questions/health`
- `POST /api/admin/question-imports`
- `POST /api/admin/question-imports/excel`
- Alias `POST /api/admin/questions/imports`
- `POST /api/admin/questions/{id}/flag`
- `GET /api/admin/questions/{id}/flags`
- `POST /api/admin/questions/{id}/flags`
- Thymeleaf page `GET /admin/questions/health`
- Thymeleaf page `GET/POST /admin/questions/import`
- Thymeleaf Excel upload `POST /admin/questions/import/excel`
- Quality filter on `/admin/questions`
- DB-backed `question_import_jobs`, `question_import_errors` and `question_flags`

## Health Contract

`GET /api/admin/questions/health?quality=WEAK_DISCRIMINATION`

`quality` is optional:

- `HIGH_ERROR`
- `NO_ATTEMPTS`
- `NEEDS_REVIEW`
- `FLAGGED`
- `WEAK_DISCRIMINATION`

Response:

```json
{
  "totalQuestions": 10,
  "withAttempts": 4,
  "highErrorQuestions": 1,
  "noAttemptQuestions": 5,
  "needsReviewQuestions": 2,
  "flaggedQuestions": 1,
  "weakDiscriminationQuestions": 1,
  "items": [
    {
      "questionId": 1,
      "currentVersionId": 1,
      "status": "published",
      "type": "SCQ",
      "topicTitleRu": "Проценты",
      "bodyRu": "Найдите 20% от 350.",
      "attempts": 12,
      "incorrectAnswers": 9,
      "errorRate": 75,
      "discriminativePower": 18,
      "openFlagCount": 1,
      "qualitySignal": "high_error"
    }
  ]
}
```

Metrics are computed through:

`AnswerEvaluation -> TestSessionQuestion -> QuestionVersion -> Question`

Current signal rules:

- `needs_review`: question status is already `NEEDS_REVIEW`
- `flagged`: current question has open flags
- `no_attempts`: current version has no answer evaluations
- `high_error`: at least 3 attempts and error rate is 70% or higher
- `weak_discrimination`: at least 6 attempts and discriminative power is below 10
- `ok`: none of the above

Discriminative power is a pragmatic MVP metric:

`correct-rate among sessions with result >= 80% - correct-rate among sessions with result < 60%`

This is not a psychometric IRT model. It is enough to surface suspicious questions for methodist review without blocking the MVP.

## Flag Contract

Send a question directly to review:

`POST /api/admin/questions/{id}/flag`

```json
{
  "reason": "content_health"
}
```

Create a quality flag without changing status:

`POST /api/admin/questions/{id}/flags`

```json
{
  "source": "COMPLAINT",
  "reason": "Текст вопроса спорный"
}
```

Supported flag sources:

- `ANALYTICS`
- `METHODIST`
- `COMPLAINT`
- `IMPORT`

## JSON Import Contract

`POST /api/admin/question-imports`

```json
{
  "questions": [
    {
      "topicId": 1,
      "type": "SCQ",
      "difficulty": 2,
      "bodyRu": "Найдите 10% от 200.",
      "bodyKk": "200 санының 10 пайызын табыңыз.",
      "source": "JSON import",
      "options": [
        {"label": "A", "textRu": "10", "textKk": "10", "correct": false},
        {"label": "B", "textRu": "20", "textKk": "20", "correct": true}
      ]
    }
  ]
}
```

Each row reuses `QuestionForm` and `QuestionBankService` validation. Imported questions are forced into `NEEDS_REVIEW`; import does not publish content.

Response:

```json
{
  "id": 1,
  "status": "partial",
  "sourceType": "JSON",
  "originalFilename": null,
  "totalRows": 3,
  "importedRows": 2,
  "errorRows": 1,
  "createdAt": "2026-04-29T00:00:00Z",
  "errors": [
    {
      "rowNo": 3,
      "errorCode": "scq_requires_exactly_one_correct",
      "message": "Для SCQ нужен ровно один правильный ответ"
    }
  ]
}
```

## Excel Import Contract

`POST /api/admin/question-imports/excel`

Multipart field: `file`, `.xlsx`.

The first sheet is parsed. Row 1 is a header. Columns:

| Column | Meaning |
| --- | --- |
| `type` | `SCQ`, `MCQ`, `MATCHING`, `FILL_IN` |
| `topicId` | Existing topic id |
| `difficulty` | 1-5 |
| `bodyRu` | RU question text |
| `bodyKk` | KK question text |
| `source` | Source label |
| `payload` | Type-specific payload |
| `correct` | Correct labels for SCQ/MCQ |
| `explanationRu` | Optional |
| `explanationKk` | Optional |

Payload formats:

- SCQ/MCQ: `A|RU|KK;B|RU|KK`, correct: `B` or `A,C`
- MATCHING: `leftRu|leftKk|rightRu|rightKk;...`
- FILL_IN: `[[1]]|30|NUMERIC_TOLERANCE|0.01`

The original filename and a compact file summary are stored on `question_import_jobs`.

## Remaining Non-Blocking Work

- For very large banks, replace live health queries with scheduled/materialized aggregates.
- If source-file retention becomes a legal/product requirement, move raw uploads into object storage instead of keeping only metadata.
- A fuller complaint workflow can add role-specific screens and resolution states; Stage 12 currently stores flags and exposes open counts.
