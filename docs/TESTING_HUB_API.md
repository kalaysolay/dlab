# Testing Hub API and Student UI

Статус: первый вертикальный срез этапа 5 реализован.

## Scope

Реализовано:

- Flyway migration `V7__testing_hub.sql`.
- JPA-модель `TestTemplate`, `TestSession`, `TestSessionQuestion`, `StudentAnswer`, `AnswerEvaluation`, `TestResult`.
- Подбор опубликованных `QuestionVersion` по предмету, классу и сложности.
- Student-facing DTO без `answer_key_json` до завершения теста.
- Сохранение ответа через API и server-rendered form flow.
- Идемпотентный `finish`: повторный вызов возвращает существующий результат и не создает дубль.
- Server-side проверка `SCQ`, `MCQ`, `MATCHING`, `FILL_IN`.
- Server-rendered student UI:
  - `GET /student/tests`
  - `GET /student/test-sessions/{id}`
  - `GET /student/test-sessions/{id}/result`
- Demo seed из четырех published questions для первого предметного среза.

Пока не реализовано:

- Асинхронное autosave после каждого клика в UI.
- Полноценное восстановление частично заполненной server-rendered формы после refresh.
- Strict Mode МОДО/СОР/СОЧ шаблоны и расширенная шкала оценивания.
- AI-разборы ошибок; в результате показывается human-reviewed explanation из версии вопроса.

## API

Все endpoints требуют роль `STUDENT`.

### `GET /api/tests/types`

Возвращает доступные типы тестов.

### `GET /api/tests/filters`

Возвращает справочники subjects/grades/topics/skills через общий content graph service.

### `POST /api/test-sessions`

Создает сессию и фиксирует набор опубликованных версий вопросов.

```json
{
  "testType": "SUBJECT",
  "subjectId": 1,
  "gradeId": 4,
  "language": "ru",
  "difficulty": 2,
  "questionCount": 10
}
```

Response содержит вопросы без правильных ответов.

### `GET /api/test-sessions/{sessionId}`

Возвращает текущую сессию только для владельца-ученика.

### `PATCH /api/test-sessions/{sessionId}/answers`

Сохраняет или заменяет ответ на вопрос сессии.

SCQ/MCQ:

```json
{
  "sessionQuestionId": 40,
  "answer": {"selected": ["B"]}
}
```

MATCHING:

```json
{
  "sessionQuestionId": 41,
  "answer": {"pairs": {"50%": "0.5", "25%": "0.25"}}
}
```

FILL_IN:

```json
{
  "sessionQuestionId": 42,
  "answer": {"answers": {"[[1]]": "30"}}
}
```

### `POST /api/test-sessions/{sessionId}/finish`

Оценивает ответы на сервере, сохраняет `AnswerEvaluation` и `TestResult`, затем обновляет `SkillMastery` для аналитики этапа 6.
Повторный вызов не создает новый `TestResult`.

### `GET /api/test-sessions/{sessionId}/result`

Возвращает результат и детализацию. Correct answers доступны только здесь.

## Validation Errors

- `student_profile_not_found`
- `subject_not_found`
- `grade_not_found`
- `published_questions_not_found`
- `test_session_not_found`
- `session_question_not_found`
- `session_finished`
- `result_not_found`
- `question_payload_invalid`
- `answer_payload_invalid`

## Security Notes

- `/api/test-sessions/**` и `/api/tests/**` закрыты ролью `STUDENT`.
- Сессия ищется по `sessionId` и текущему `student_profile_id`.
- `answer_key_json` читается только сервисом проверки и не попадает в DTO активной сессии.

## Stabilization Update (2026-05-01)

- Added regression coverage for `FILL_IN` result rendering:
  - result page should not contain `[object Object]`;
  - placeholder markers (e.g. `[[1]]`) remain visible for client-side fill result rendering.
- Hardened client-side result formatter for object payloads:
  - object values are normalized via JSON stringification/fallback instead of raw object coercion;
  - nested fill answer objects now resolve to displayable scalar values.
