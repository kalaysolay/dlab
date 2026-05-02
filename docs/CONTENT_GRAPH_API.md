# Content Graph API and Admin UI

Статус: этап 3 закрыт для перехода к банку вопросов.

## Scope

Реализовано:

- JPA-модель и миграции для `Subject`, `Grade`, `Topic`, `AtomicSkill`.
- Seed-цепочка: `Математика -> 4 класс -> Проценты -> Нахождение процента от числа -> Вычислять процент от числа`.
- CRUD тем через admin API.
- CRUD атомарных навыков через admin API и Thymeleaf UI.
- Дерево тем через admin API и Thymeleaf UI.
- Проверка дублей в рамках `subject + grade + parent` по `code`, `titleRu`, `titleKk`.
- Проверка дублей атомарных навыков в рамках темы.
- Запрет удаления темы, если у нее есть дочерние темы или атомарные навыки.
- Запрет удаления темы или атомарного навыка, если на них уже ссылаются версии вопросов.
- Audit log для операций создания, изменения и удаления тем/навыков.
- Общие справочники для будущих фильтров вопросов и лекций.

Пока не реализовано:

- Проверка удаления темы по лекциям и результатам. Зависимость от вопросов уже добавлена в этапе 4.

## Admin API

Все `/api/admin/**` endpoints требуют роль `ADMIN` и CSRF для изменяющих запросов в browser session.

### Subjects

`GET /api/admin/subjects`

Возвращает:

```json
[
  {
    "id": 1,
    "code": "math",
    "titleRu": "Математика",
    "titleKk": "Математика"
  }
]
```

### Grades

`GET /api/admin/grades`

Возвращает:

```json
[
  {
    "id": 4,
    "gradeNo": 4,
    "titleRu": "4 класс",
    "titleKk": "4 сынып"
  }
]
```

### Topics

`GET /api/admin/topics?subjectId={id}&gradeId={id}`

`subjectId` и `gradeId` опциональны; если не переданы, сервис берет первые доступные справочники.

`POST /api/admin/topics`

```json
{
  "subjectId": 1,
  "gradeId": 4,
  "parentId": 10,
  "code": "finding-percent-of-number",
  "titleRu": "Нахождение процента от числа",
  "titleKk": "Санның пайызын табу"
}
```

`PATCH /api/admin/topics/{id}` использует тот же payload.

`DELETE /api/admin/topics/{id}` возвращает `204`, если удаление безопасно.

Ошибки:

- `topic_duplicate` - дубль в той же ветке.
- `topic_has_children` - есть дочерние темы.
- `topic_has_skills` - есть атомарные навыки.
- `topic_has_questions` - есть привязанные версии вопросов.
- `topic_parent_scope_mismatch` - родитель из другого предмета или класса.
- `topic_parent_cycle` - попытка сделать тему дочерней для себя или потомка.
- `skill_duplicate` - дубль навыка в теме.
- `skill_topic_mismatch` - `topicId` в path и body не совпадают.
- `skill_has_questions` - есть привязанные версии вопросов.

### Topic Tree

`GET /api/admin/topics/tree?subjectId={id}&gradeId={id}`

Возвращает рекурсивное дерево:

```json
[
  {
    "id": 1,
    "code": "percent-basics",
    "titleRu": "Проценты",
    "titleKk": "Пайыздар",
    "children": []
  }
]
```

### Topic Skills

`GET /api/admin/topics/{id}/skills`

Возвращает список атомарных навыков темы.

`POST /api/admin/topics/{id}/skills`

```json
{
  "topicId": 11,
  "code": "calculate-percent-value",
  "titleRu": "Вычислять процент от числа",
  "titleKk": "Санның пайызын есептеу",
  "active": true
}
```

`PATCH /api/admin/skills/{id}` использует тот же payload.

`DELETE /api/admin/skills/{id}` возвращает `204`.

## Shared References

`GET /api/content/references?subjectId={id}&gradeId={id}` доступен авторизованным пользователям и нужен как общий источник фильтров для следующих этапов: банк вопросов и лекции.

## Admin UI

- `GET /admin/topics` - список тем, фильтр по предмету/классу, создание темы, безопасное удаление.
- `GET /admin/topics/tree` - дерево тем, форма редактирования выбранного узла и CRUD атомарных навыков выбранной темы.

UI остается server-rendered Thymeleaf, без отдельного SPA.
