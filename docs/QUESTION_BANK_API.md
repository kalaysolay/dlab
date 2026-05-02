# Question Bank API and Admin UI

Статус: первый вертикальный срез этапа 4 реализован.

## Scope

Реализовано:

- JPA-модель `Question` и `QuestionVersion`.
- Flyway-миграция `V6__question_bank.sql`.
- Типы вопросов `SCQ`, `MCQ`, `MATCHING`, `FILL_IN`.
- Статусы `draft`, `needs_review`, `approved`, `published`, `archived`.
- Серверная validation-логика для структуры ответа каждого типа.
- Версионирование: правка `approved` или `published` вопроса создает новую версию и переводит вопрос в `needs_review`.
- Admin REST endpoints для списка, создания, обновления, approve, publish и archive.
- Server-rendered admin UI:
  - `GET /admin/questions` - список, фильтры, moderation actions.
  - `GET /admin/questions/new` - ручное создание вопроса.
- Audit log для создания, обновления, approve, publish и archive вопросов.
- Защита удаления тем и атомарных навыков, если на них уже ссылаются версии вопросов.

Пока не реализовано:

- Полноценная карточка редактирования существующего вопроса в UI.
- Массовые действия и import Excel/JSON.
- Content health метрики по качеству вопросов.
- Использование published questions в Testing Hub. Это следующий этап MVP-цикла.

## DTO

`QuestionForm` используется для REST JSON и server-rendered формы.

Решение от 2026-04-30:

- Ручное создание вопроса не требует `subjectId` или `gradeId` в URL `/admin/questions/new`. Предмет и класс выбираются внутри формы, а список тем обновляется через `/api/content/references`.
- Удаление варианта ответа в админской форме выполняется по конкретной строке через подтверждение, а не через действие "удалить последний".
- `ChoiceOptionForm` поддерживает `softDeleted` и JSON alias `soft_delete`. Такие варианты игнорируются при SCQ/MCQ-валидации, сборке `options_json`, `answer_key_json` и тексте правильного ответа для мини-лекции.
- После soft-delete должно оставаться минимум два активных варианта ответа.

Общие поля:

```json
{
  "topicId": 11,
  "atomicSkillId": 3,
  "type": "SCQ",
  "difficulty": 2,
  "bodyRu": "Найдите 20% от 350.",
  "bodyKk": "350 санының 20 пайызын табыңыз.",
  "source": "Ручной ввод",
  "explanationRu": "20% = 0.2, 350 * 0.2 = 70.",
  "explanationKk": "20% = 0.2, 350 * 0.2 = 70.",
  "status": "DRAFT"
}
```

`status` при создании и обычном обновлении принимает только рабочие состояния `DRAFT` или `NEEDS_REVIEW`. `APPROVED`, `PUBLISHED` и `ARCHIVED` выставляются отдельными endpoints.

## Type Payloads

### SCQ

`SCQ` требует минимум два активных варианта и ровно один `correct=true`. Варианты с `softDeleted=true` / `soft_delete=true` перед проверкой исключаются.

```json
{
  "topicId": 11,
  "type": "SCQ",
  "difficulty": 2,
  "bodyRu": "Найдите 20% от 350.",
  "bodyKk": "350 санының 20 пайызын табыңыз.",
  "source": "Ручной ввод",
  "options": [
    {"label": "A", "textRu": "60", "textKk": "60", "correct": false},
    {"label": "B", "textRu": "70", "textKk": "70", "correct": true},
    {"label": "C", "textRu": "75", "textKk": "75", "correct": true, "soft_delete": true}
  ]
}
```

### MCQ

`MCQ` требует минимум два активных варианта и минимум один `correct=true`. Варианты с `softDeleted=true` / `soft_delete=true` перед проверкой исключаются.

### MATCHING

`MATCHING` требует минимум две пары. Ключ соответствия хранится по строкам: левая часть строки соответствует правой части той же строки.

```json
{
  "topicId": 11,
  "type": "MATCHING",
  "difficulty": 3,
  "bodyRu": "Соотнесите проценты и дроби.",
  "bodyKk": "Пайыздар мен бөлшектерді сәйкестендіріңіз.",
  "source": "Ручной ввод",
  "matchingPairs": [
    {"leftRu": "50%", "leftKk": "50%", "rightRu": "0.5", "rightKk": "0.5"},
    {"leftRu": "25%", "leftKk": "25%", "rightRu": "0.25", "rightKk": "0.25"}
  ]
}
```

### FILL_IN

`FILL_IN` требует минимум один answer key и match mode.

```json
{
  "topicId": 11,
  "type": "FILL_IN",
  "difficulty": 2,
  "bodyRu": "15% от 200 равно [[1]].",
  "bodyKk": "200 санының 15 пайызы [[1]].",
  "source": "Ручной ввод",
  "fillAnswers": [
    {
      "placeholder": "[[1]]",
      "answer": "30",
      "matchMode": "NUMERIC_TOLERANCE",
      "tolerance": 0.01
    }
  ]
}
```

Поддержанные `matchMode`:

- `EXACT`
- `NORMALIZED`
- `NUMERIC_TOLERANCE`
- `REGEXP`

## Admin API

Все `/api/admin/**` endpoints требуют роль `ADMIN` и CSRF для изменяющих запросов в browser session.

`GET /api/admin/questions`

Фильтры:

- `topicId`
- `status`
- `type`
- `query`

`GET /api/admin/questions/{id}`

`POST /api/admin/questions`

Создает логический вопрос и первую версию.

`PATCH /api/admin/questions/{id}`

Обновляет текущую версию для draft/review вопроса. Для `approved` и `published` создает новую версию и переводит статус в `needs_review`.

`POST /api/admin/questions/{id}/approve`

Переводит вопрос в `approved`.

`POST /api/admin/questions/{id}/publish`

Переводит вопрос в `published` и ставит `published_at` на текущей версии.

`POST /api/admin/questions/{id}/archive`

Переводит вопрос в `archived`.

## Validation Errors

Основные коды:

- `question_type_required`
- `topic_not_found`
- `skill_not_found`
- `skill_topic_mismatch`
- `question_body_required`
- `question_source_required`
- `question_difficulty_invalid`
- `choice_requires_two_options`
- `choice_option_text_required`
- `scq_requires_exactly_one_correct`
- `mcq_requires_one_correct`
- `matching_requires_two_pairs`
- `matching_pair_required`
- `fill_in_requires_answer`
- `fill_in_answer_required`
- `fill_in_tolerance_required`
- `question_archived`

## Admin UI

UI остается server-rendered Thymeleaf без отдельного SPA.

- `GET /admin/questions` - фильтры по предмету/классу/теме/типу/статусу/поиску, список вопросов и действия approve/publish/archive.
- `GET /admin/questions/new` - ручная форма создания с переключением панелей типов. Прямые ссылки не должны заранее привязывать предмет/класс; эти параметры выбираются во время создания вопроса.

Клиентский JS только переключает панели типов. Все правила ответа повторно проверяются на backend.
