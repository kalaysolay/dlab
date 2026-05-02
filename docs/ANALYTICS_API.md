# Analytics API and Knowledge Profile

Статус: первый вертикальный срез этапа 6 реализован.

## Scope

Реализовано:

- Flyway migration `V8__analytics_mastery.sql`.
- JPA-модель `SkillMastery`.
- Пересчет mastery после первого `POST /api/test-sessions/{sessionId}/finish`.
- Расчет по теме и по атомарному навыку, если `QuestionVersion.atomicSkill` задан.
- Timeline результатов на базе `test_results`.
- Последние ошибки на базе `answer_evaluations`.
- Knowledge map на базе `skill_mastery`.
- Базовая trajectory и prediction без ML: объяснимые агрегаты по последним результатам.
- Student UI: `GET /student/analytics`.
- Parent child card показывает mastery, слабые темы и последние ошибки только для привязанного ребенка.

Пока не реализовано:

- Полная ML-персонализация и продвинутый predictive scoring.
- Spaced repetition/recommendation queue.
- Content health dashboard на базе статистики вопросов.
- Backfill mastery для результатов, созданных до этапа 6.

## Mastery Formula

При завершении теста analytics читает `answer_evaluations` и группирует ответы:

- по `topic`;
- по `atomicSkill`, если он есть у версии вопроса.

Первая попытка задает mastery как weighted attempt score.
Последующие попытки используют простую объяснимую формулу:

```text
new_mastery = old_mastery * 0.7 + attempt_score * difficulty_weight * 0.3
```

`difficulty_weight` ограничен диапазоном сложности `1..5`.
Значение mastery хранится в процентах `0..100`.

## API

Все endpoints требуют роль `STUDENT` или `PARENT`.

Доступ:

- ученик видит только свой `studentId`;
- родитель видит только детей, связанных через `parent_student_links`;
- при отсутствии доступа возвращается `404` с `student_analytics_not_found`.

### `GET /api/analytics/student/{studentId}/timeline`

Возвращает последние 10 результатов тестов.

### `GET /api/analytics/student/{studentId}/knowledge-map`

Возвращает строки mastery по темам и навыкам:

```json
{
  "topicId": 1,
  "topicTitle": "Нахождение процента",
  "atomicSkillId": 1,
  "atomicSkillTitle": "Вычисление процента от числа",
  "masteryPercent": 72,
  "attempts": 2,
  "correctAnswers": 5,
  "totalQuestions": 8,
  "status": "watch"
}
```

`status`:

- `weak` - ниже 50%;
- `watch` - 50-74%;
- `strong` - 75% и выше.

### `GET /api/analytics/student/{studentId}/last-errors`

Возвращает последние 5 ошибок. Correct answer не раскрывается в этом DTO; для детального разбора правильных ответов используется result page после завершения теста.

### `GET /api/analytics/student/{studentId}/trajectory`

Возвращает базовый следующий фокус по слабым темам.

### `GET /api/analytics/student/{studentId}/prediction`

Возвращает простой прогноз по среднему проценту последних результатов. Это вероятностная подсказка, не гарантия результата.

## UI

- `GET /student/analytics` - аналитика текущего ученика.
- `GET /parent/children/{studentId}` - карточка ребенка с analytics summary.
- Result page содержит переход к `/student/analytics`.
- Authenticated header должен показывать `Кабинет` и `Выйти`; anonymous `Регистрация` и `Войти` скрываются после входа.

## Browser QA

27 апреля 2026 проверено через локальный Chrome fallback на `http://localhost:18080`:

- вход `student@damulab.kz / password`;
- запуск и завершение предметного среза по математике, 4 класс;
- переход из result page в analytics;
- desktop screenshot: `build/qa/analytics-desktop.png`;
- mobile viewport 390x844 screenshot: `build/qa/analytics-mobile.png`;
- горизонтальный overflow на mobile отсутствует.

## Security Notes

- Серверная проверка ответов остается в Testing Hub.
- Analytics не читает `answer_key_json` для last-errors.
- Parent visibility проверяется через `ParentStudentLinkRepository`, а не через UI.
