# Схема seed JSON (Damulab question seed)

Файл: `content/seed/{gradeNo}/{topicSlug}/questions.json`

Id БД (`subjectId`, `topicIds`, `gradeIds`) **не** входят в seed — маппинг при импорте.

## Корень

```json
{
  "meta": {
    "subjectRu": "string",
    "subjectKk": "string",
    "gradeNo": 4,
    "topicRu": "string",
    "topicKk": "string",
    "topicSlug": "procenty-4",
    "atomicSkillRu": "string|null",
    "atomicSkillKk": "string|null",
    "sourceRef": "учебник / файл / страницы",
    "illustrationPolicy": "none|explicit|visual-topics",
    "generatedFor": "seed"
  },
  "questions": [],
  "imageJobs": []
}
```

## Вопрос

Общие поля:

| Поле | Тип | Обязательно |
|------|-----|-------------|
| `localId` | string (`q01`…) | да |
| `type` | `SCQ`\|`MCQ`\|`MATCHING`\|`FILL_IN` | да |
| `difficulty` | int 1..5 | да |
| `bodyRu` / `bodyKk` | string | да |
| `explanationRu` / `explanationKk` | string | желательно |
| `source` | string | да |
| `needsIllustration` | boolean | да (дефолт логики: false) |
| `illustration` | object\|null | да (null если нет) |
| `options` | array | для SCQ/MCQ |
| `matchingPairs` | array | для MATCHING |
| `fillAnswers` | array | для FILL_IN |

### options (SCQ/MCQ)

```json
{"label": "A", "textRu": "...", "textKk": "...", "correct": false}
```

### matchingPairs

```json
{"leftRu": "...", "leftKk": "...", "rightRu": "...", "rightKk": "..."}
```

### fillAnswers

```json
{
  "placeholder": "[[1]]",
  "answer": "30",
  "matchMode": "NUMERIC_TOLERANCE",
  "tolerance": 0.01
}
```

`matchMode`: `EXACT` | `NORMALIZED` | `NUMERIC_TOLERANCE` | `REGEXP`.

### illustration (только если разрешено policy)

```json
{
  "filename": "procenty-4-q03-pie.png",
  "prompt": "Школьная схема: ... Не показывай правильный ответ.",
  "widthHint": 800,
  "heightHint": 500
}
```

В `bodyRu` и `bodyKk` — один и тот же маркер: `{{IMG:procenty-4-q03-pie.png}}`.

## imageJobs

Дедуп по `filename`. Пустой массив, если `illustrationPolicy: none`.

```json
{
  "filename": "procenty-4-q03-pie.png",
  "prompt": "...",
  "questionLocalId": "q03"
}
```
