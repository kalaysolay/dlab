# Damulab.kz: подробный план реализации

Статус: рабочий план для инженерных агентов.
Дата подготовки: 23 апреля 2026.

План ориентирован на простую надежную реализацию: монолитное Spring Boot приложение, PostgreSQL, серверный рендеринг основных страниц, легкая клиентская интерактивность и постепенное подключение real-time/AI/push модулей.

## 1. Принципы реализации

1. Сначала закрыть базовый учебный цикл: контент -> тест -> результат -> аналитика -> родитель.
2. Не строить сложную микросервисную архитектуру до появления реальной нагрузки.
3. Не публиковать AI-контент без человеческой модерации.
4. Все пользовательские ответы и результаты считать на сервере.
5. Держать HTML-макеты как источник UI-сценариев, но переносить их в общие шаблоны и компоненты.
6. Делать функциональные вертикали end-to-end, а не только backend или только UI.

## 2. Приоритеты

### P0: обязательный MVP

- Auth и роли.
- Профили ученика и родителя.
- Привязка родитель-ребенок.
- Граф знаний и темы.
- Банк вопросов с четырьмя типами.
- Тестовая сессия и результат.
- Базовая аналитика по темам.
- Админка тем, вопросов и лекций.
- Ручные push-уведомления.
- RU/KK интерфейс.

### P1: важное после MVP

- AI-генерация вопросов и мини-лекций.
- Quality-check и moderation flags.
- Spaced repetition.
- Миссия дня, streak, достижения.
- LMS mobile/PWA просмотр лекций с checkpoint-вопросами.
- Викторины с комнатами до 4 игроков.

### P2: развитие

- Predictive scoring.
- Продвинутый content health dashboard.
- Экспорт отчетов PDF/WhatsApp.
- Рейтинги и лиги.
- Интеграции с внешними системами.
- Массовый импорт Excel/JSON.

## 3. Этап 0: подготовка проекта

Цель: создать технический фундамент, чтобы агенты не расходились в стиле и структуре.

Задачи:

- Создать Spring Boot 3.x проект на Java 21.
- Подключить PostgreSQL, Flyway, Spring Security, Thymeleaf, validation, test stack.
- Настроить профили `local`, `test`, `prod`.
- Настроить Docker Compose для приложения и PostgreSQL.
- Настроить базовый layout Thymeleaf: header, sidebar, language switch, alerts, modal shell.
- Перенести общие стили из HTML-макетов в сборку CSS.
- Подготовить seed-данные: предметы, классы, тестовые темы, demo-пользователи.
- Создать правила кодстайла и структуру пакетов.

Результат:

- Приложение стартует локально.
- Есть пустой дашборд для ролей.
- Миграции применяются с нуля.
- Тесты запускаются одной командой.

Критерии приемки:

- `GET /` открывает главную.
- `GET /login` открывает вход.
- `GET /admin` требует роль администратора.
- Flyway создает схему без ошибок.

## 4. Этап 1: Auth, роли и профили

Цель: дать системе безопасную идентичность.

Backend:

- Реализовать `User`, `Role`, `StudentProfile`, `ParentProfile`.
- Реализовать регистрацию, вход и выход. Email в MVP используется как логин и не требует подтверждения.
- Реализовать хеширование паролей.
- Реализовать текущего пользователя `GET /api/me`.
- Реализовать базовую RBAC-проверку.

Frontend:

- Перенести `auth.html` в Thymeleaf.
- Реализовать role switch: родитель/ученик.
- Перенести `profile.html`.
- Добавить серверные ошибки формы.

Тесты:

- Регистрация с уникальным email.
- Ошибка при дубле email.
- Вход с валидным паролем.
- Запрет доступа к админке для ученика.

Риски:

- Email confirmation, password reset через email и email-рассылки не входят в MVP до настройки SMTP. Не добавлять SMTP-зависимость в этап 1.

## 5. Этап 2: родительская связка

Цель: родитель может добавить ребенка и видеть его карточку.

Backend:

- Реализовать `ParentStudentLink`.
- Реализовать создание ребенка родителем.
- Реализовать одноразовый код/QR привязки.
- Реализовать ограничения доступа родителя к данным ребенка.

Frontend:

- Перенести `parent-home.html`.
- Реализовать модалку добавления ребенка через форму.
- Реализовать генерацию кода/QR.
- Реализовать карточку ребенка и детальное окно.

Тесты:

- Родитель создает ребенка.
- Родитель видит только своих детей.
- Код привязки нельзя использовать повторно.
- Истекший код не работает.

Критерии приемки:

- Родитель после входа видит список детей.
- Можно добавить demo-ребенка и открыть его карточку.

## 6. Этап 3: граф знаний и справочники

Цель: создать основу для вопросов, лекций и аналитики.

Backend:

- Реализовать `Subject`, `Grade`, `Topic`, `AtomicSkill`.
- Реализовать CRUD тем.
- Реализовать дерево тем.
- Реализовать проверку дублей.
- Реализовать защиту удаления темы с зависимостями.

Frontend:

- Перенести `admin/mockups/topics-list.html`.
- Перенести `admin/mockups/topic-tree-editor.html`.
- Сделать общий admin layout и sidebar.
- Реализовать пустые состояния и validation errors.

Тесты:

- Создание темы.
- Обнаружение дубля.
- Переименование темы.
- Запрет удаления темы с зависимыми вопросами.

Критерии приемки:

- Методист может создать дерево `Математика -> 4 класс -> Проценты -> Нахождение процента`.
- Тема доступна в фильтрах вопросов и лекций.

## 7. Этап 4: банк вопросов

Цель: методист может создавать и модерировать вопросы.

Статус на 26 апреля 2026: первый вертикальный срез реализован. Есть модель `Question/QuestionVersion`, DTO `QuestionForm`, validation для `SCQ/MCQ/MATCHING/FILL_IN`, REST endpoints, server-rendered список и форма создания. Использование published questions в Testing Hub остается задачей этапа 5.

Backend:

- Реализовать сущности вопроса и версии.
- Реализовать типы `SCQ`, `MCQ`, `MATCHING`, `FILL_IN`.
- Реализовать DTO и validation для каждого типа.
- Реализовать статусы: draft, needs_review, approved, published, archived.
- Реализовать версионирование при редактировании опубликованного вопроса.
- Реализовать фильтры банка вопросов.

Frontend:

- Перенести `questions-list.html`.
- Перенести `question-create-manual.html`.
- Реализовать динамические панели типов вопросов.
- Реализовать мини-лекцию RU/KK.
- Реализовать форму ошибок перед сохранением.

Тесты:

- `SCQ` требует ровно один правильный ответ.
- `MCQ` требует минимум один правильный ответ.
- `MATCHING` требует ключ соответствий.
- `FILL_IN` требует правила проверки.
- Опубликованный вопрос редактируется через новую версию.

Критерии приемки:

- Методист создает вопрос каждого типа.
- Вопрос можно одобрить и использовать в тесте.

## 8. Этап 5: Testing Hub

Цель: ученик проходит тест, система сохраняет ответы и считает результат.

Статус на 26 апреля 2026: первый вертикальный срез реализован. Есть таблицы `test_templates`, `test_sessions`, `test_session_questions`, `student_answers`, `answer_evaluations`, `test_results`; published questions подбираются из банка вопросов, ответы сохраняются, `finish` идемпотентен, проверка `SCQ/MCQ/MATCHING/FILL_IN` выполняется на сервере. Добавлены server-rendered страницы `/student/tests`, `/student/test-sessions/{id}`, `/student/test-sessions/{id}/result`. Этап 6 должен подключить mastery/analytics/parent visibility к сохраненным результатам.

Backend:

- Реализовать `TestTemplate`, `TestSession`, `TestSessionQuestion`, `StudentAnswer`, `AnswerEvaluation`, `TestResult`.
- Реализовать подбор вопросов по типу теста, предмету, классу, языку и сложности.
- Реализовать создание session ID.
- Реализовать сохранение ответа.
- Реализовать finish с идемпотентностью.
- Реализовать server-side проверку ответов.
- Реализовать результат и детализацию.

Frontend:

- Перенести `student-tests.html`.
- Перенести `student-test-session.html`.
- Перенести `student-test-result.html`.
- Реализовать таймер.
- Реализовать навигацию отвечено/не отвечено.
- Реализовать MATCHING на SVG.
- Реализовать FILL_IN.
- Реализовать подтверждение выхода и завершения.
- Реализовать восстановление незавершенной сессии.

Тесты:

- Создание сессии.
- Сохранение ответа.
- Проверка каждого типа вопроса.
- Повторный finish не создает дубль.
- Правильные ответы недоступны до завершения.

Критерии приемки:

- Ученик выбирает предмет, класс, язык и начинает тест.
- Ученик отвечает на вопросы всех типов.
- После завершения видит балл и детализацию.
- Результат сохраняется в истории.

## 9. Этап 6: аналитика и цифровой профиль знаний

Цель: результаты тестов превращаются в понятную аналитику.

Статус на 27 апреля 2026: первый вертикальный срез реализован. Есть таблица `skill_mastery`, пересчет mastery после завершения теста, REST endpoints `/api/analytics/student/{studentId}/timeline|knowledge-map|last-errors|trajectory|prediction`, student UI `/student/analytics` и parent visibility в карточке ребенка. Прогноз и траектория в MVP являются простыми объяснимыми агрегатами без ML.

Backend:

- Реализовать `SkillMastery`.
- Реализовать расчет mastery по теме и навыку.
- Реализовать timeline результатов.
- Реализовать последние ошибки.
- Реализовать облако знаний.
- Реализовать базовую траекторию развития.

Frontend:

- Перенести `analytics.html`.
- Подключить его к данным.
- Добавить переходы из результата теста к аналитике.
- Обновить `parent-home.html` детальными данными ребенка.

Тесты:

- Результат теста обновляет mastery.
- Ошибка попадает в последние ошибки.
- Родитель видит аналитику только своего ребенка.

Критерии приемки:

- После теста меняется облако знаний.
- Родитель видит слабые темы ребенка.

## 10. Этап 7: ученический дашборд, streak и достижения

Цель: сделать регулярную активность и понятную главную страницу ученика.

Статус на 27 апреля 2026: этап закрыт первым вертикальным срезом. Есть таблицы `streaks`, `achievements`, `student_achievements`, выдача достижений после первого идемпотентного finish тестовой сессии, обновление streak один раз в UTC-день, server-rendered `/student` с миссией дня/последним занятием/прогрессом/достижениями, `GET /api/student/dashboard`, настройки уведомлений в профиле ученика и сохранение языка из общего header selector. Browser/mobile QA для `/student` и `/student/profile` выполнен на localhost. Полноценная экономика энергии, streak freeze, интервальные повторения и quiz/Arena-достижения остаются post-MVP.

Backend:

- Реализовать `Streak`, `Achievement`, `StudentAchievement`.
- Реализовать миссию дня.
- Реализовать последнее занятие.
- Реализовать виджет прогресса.
- Реализовать настройки уведомлений профиля.

Frontend:

- Перенести `student-home.html`.
- Перенести `student-profile.html`.
- Унифицировать модалку достижений.
- Подключить language selector к настройкам.

Тесты:

- Streak обновляется при полезной активности.
- Повторная активность в тот же день не увеличивает streak повторно.
- Достижение выдается один раз.

Критерии приемки:

- Ученик видит актуальную миссию, streak и достижения.
- Из профиля можно изменить класс, язык и уведомления.

## 11. Этап 8: лекции и LMS

Цель: методист создает лекции, ученик читает и проходит контроль понимания.

Статус на 27 апреля 2026: первый вертикальный срез и remainder QA завершены. Есть таблицы `lectures`, `lecture_versions`, `lecture_attachments`, `lecture_checkpoints`; REST endpoints `/api/admin/lectures`, server-rendered admin UI списка/редактора/preview, student UI `/student/lectures` и `/student/lectures/{id}`. Публикация требует тему и заполненные RU/KK название/контент; draft может быть частично заполнен. `AUTO` checkpoint mode выбирает опубликованные вопросы той же темы, но полноценный интерактивный checkpoint-flow ученика остается post-MVP. Visual/mobile QA пройден на desktop, 390px и 360px; page-level horizontal overflow устранен. Rich editor для этапа 8 намеренно оставлен как безопасный textarea fallback без CDN; локальный Quill/KaTeX bundle вынесен в отдельный asset task.

Backend:

- Реализовать `Lecture`, `LectureVersion`, `LectureAttachment`, `LectureCheckpoint`.
- Реализовать CRUD лекций.
- Реализовать публикацию лекции.
- Реализовать режимы контроля: none, auto, manual.
- Реализовать безопасное хранение HTML/Delta контента.

Frontend:

- Перенести `lectures-list.html`.
- Перенести `lecture-editor.html`.
- Перенести `lecture-view-mobile.html`.
- Реализовать fallback textarea.
- Отдельный asset task после этапа 8: подключить Quill/KaTeX локально или через другие контролируемые ассеты без production CDN.

Тесты:

- Лекция без темы не публикуется.
- RU/KK сохраняются отдельно.
- Контроль auto выбирает вопросы из банка.
- Вложения отображаются в lecture view.

Критерии приемки:

- Методист публикует лекцию с формулой.
- Ученик открывает лекцию на мобильной ширине.

## 12. Этап 9: AI Content Factory

Цель: ускорить создание вопросов и объяснений, сохранив human-in-the-loop.

Перед стартом этапа обязательно читать `docs/AI_SAFETY_BASELINE.md`. Внешним AI-провайдерам нельзя отправлять персональные данные, прямые идентификаторы ученика/родителя или связанную с конкретным ребенком сырую историю результатов. Разрешен только минимизированный образовательный контекст: предмет, класс, язык, тема/skill, тип вопроса, текст вопроса, обезличенный паттерн ошибки или агрегированный уровень. AI-результат всегда остается черновиком до проверки методистом.

Статус на 27 апреля 2026: этап 9 закрыт в текущем scope без включения real external calls по умолчанию. Добавлены `AiProvider`, `StubAiProvider`, OpenAI primary adapter, DeepSeek fallback adapter, provider router через server-side config/feature flag, `AiGenerationJob`, review batch, admin UI `/admin/questions/ai-generate`, approve/edit/delete human-in-the-loop, prompt builder, draft schema validation и тесты на job creation, failure/retry, no-autopublish, provider feature flag и outbound DTO без PII/direct identifiers. Подробные DTO-границы зафиксированы в `docs/AI_CONTENT_FACTORY.md`. Текущее решение по реальным провайдерам: OpenAI как основной, DeepSeek как запасной/более дешевый.

Backend:

- Создать `AiProvider` interface.
- Начать с `StubAiProvider` без внешних network calls.
- Реализовать адаптеры для выбранного провайдера. Статус: OpenAI/DeepSeek adapters добавлены, real calls выключены feature flag по умолчанию.
- Реализовать `AiGenerationJob`.
- Реализовать промпты для генерации вопросов.
- Реализовать quality-check.
- Реализовать сохранение пачки AI-вопросов.
- Реализовать генерацию мини-лекции.

Frontend:

- Перенести `question-ai-generate.html`.
- Реализовать состояния: loading, error, result, problem batch.
- Реализовать approve/edit/delete для каждого результата.

Тесты:

- AI job можно создать.
- При сбое показывается retry.
- AI-результат не публикуется без approve.
- Параметры job сохраняются для аудита.
- Outbound provider DTO не содержит `student_id`, `parent_id`, email, phone, full name, link codes или другие прямые идентификаторы.

Критерии приемки:

- Методист генерирует пачку из 5 вопросов.
- Может одобрить один вопрос, исправить второй, удалить третий.

## 13. Этап 10: push-уведомления

Цель: оператор вручную планирует push для учеников.

Статус на 28 апреля 2026: этап 10 дозакрыт в текущем MVP-срезе. Добавлены `PushNotification`, `PushDeliveryLog`, `DeviceToken`, REST API из `pushNotification.md`, scheduler worker, `PushProvider` + `StubPushProvider`, admin UI `/admin/push-notifications`, server-side validation, edit/cancel до отправки, targeted tests, full test/build и browser/mobile QA ручных admin-сценариев на desktop, 390px и 360px. Production push provider, real device-token registration и browser QA реальной PWA push-доставки остаются отдельными gaps.

Backend:

- Реализовать `PushNotification`, `PushDeliveryLog`, `DeviceToken`.
- Реализовать API из `pushNotification.md`.
- Реализовать scheduler отправки.
- Реализовать статусы `scheduled`, `sent`, `cancelled`, `failed`.
- Реализовать target payload validation.

Frontend:

- Перенести `admin/mockups/push-notifications.html`.
- Реализовать счетчик 120 символов.
- Реализовать server time label.
- Реализовать preview.
- Реализовать таблицу и фильтр статусов.
- Реализовать edit/cancel до отправки.

Тесты:

- Нельзя создать push с пустым текстом.
- Нельзя создать push в прошлое.
- Для `subject_test` нужен `subject_id`.
- Отправленное уведомление нельзя редактировать.
- Cancelled не отправляется.

Критерии приемки:

- Оператор создает push на будущую дату.
- В назначенное время появляется попытка отправки и лог.

## 14. Этап 11: викторины и Arena

Цель: реализовать короткие игровые сессии до 4 игроков.

Статус на 28 апреля 2026: этап 11 дозакрывается live-срезом. Уже добавлены DB-backed `QuizRoom`, `QuizParticipant`, `QuizRound`, `QuizAnswer`, `QuizResult`, REST endpoints `/api/quiz/rooms`, student UI `/student/quiz`, `/student/quiz/rooms/{code}`, `/student/quiz/rooms/{code}/results`, серверная проверка ответов через `AnswerChecker`, WebSocket/STOMP endpoint `/ws/quiz`, события `/topic/quiz.rooms.{code}`, строгие серверные окна раундов, timeout-заполнение пропущенных ответов нулем и тесты lifecycle/timeout/late/access. DTO-граница зафиксирована в `docs/QUIZ_ARENA.md`: публичные room/round responses не возвращают `answer_key_json` и правильные ответы до результатов. Matchmaking/rankings и production Arena hardening остаются следующими срезами.

Backend:

- Реализовать `QuizRoom`, `QuizParticipant`, `QuizRound`, `QuizAnswer`, `QuizResult`.
- Реализовать генерацию короткого кода.
- Реализовать join/ready/start.
- Реализовать round timer.
- Реализовать подсчет результата.
- Для live-обновлений использовать WebSocket только в этом модуле.

Frontend:

- Перенести `quiz-hub.html`.
- Перенести `quiz-create-room.html`.
- Перенести `quiz-room-host.html`.
- Перенести `quiz-room-guest.html`.
- Перенести `quiz-countdown.html`, `quiz-question.html`, `quiz-round-wait.html`, `quiz-results.html`.

Тесты:

- Комната создается с уникальным кодом.
- Игрок входит по коду.
- Хост стартует раунд.
- Ответы считаются.
- Результаты отображаются всем участникам.

Критерии приемки:

- Два demo-пользователя проходят викторину end-to-end.

## 15. Этап 12: контент health dashboard и импорт

Цель: дать методистам контроль качества и ускорить загрузку базы.

Статус на 29 апреля 2026: этап 12 закрыт в текущем engineering scope. Добавлены health-метрики по текущим версиям вопросов (`attempts`, `incorrectAnswers`, `errorRate`, `discriminativePower`, `openFlagCount`, `qualitySignal`) через `AnswerEvaluation -> TestSessionQuestion -> QuestionVersion`, REST `GET /api/admin/questions/health`, отдельный серверный dashboard `/admin/questions/health`, явное действие `POST /api/admin/questions/{id}/flag` для перевода проблемного вопроса в `needs_review`, флаги/жалобы `question_flags`, JSON и Excel `.xlsx` import jobs/errors (`question_import_jobs`, `question_import_errors`), REST `POST /api/admin/question-imports`, `POST /api/admin/question-imports/excel`, серверный экран `/admin/questions/import` и quality-фильтр в банке вопросов. DTO/API границы зафиксированы в `docs/CONTENT_HEALTH_IMPORT.md`; materialized aggregates и object-storage retention для исходных файлов остаются масштабированием, не блокирующим этап.

Backend:

- Реализовать метрики по вопросам: процент ошибок, discriminative power, жалобы, флаги.
- Реализовать импорт Excel/JSON.
- Реализовать `ImportJob` и `ImportError`.

Frontend:

- Расширить банк вопросов фильтрами качества.
- Добавить экран импорта.
- Добавить карточки проблемных вопросов.

Тесты:

- Некорректный JSON не импортируется.
- Ошибки импорта показываются построчно.
- Проблемный вопрос попадает в needs_review.

Критерии приемки:

- Методист загружает пачку вопросов и видит ошибки валидации.

## 16. План переноса HTML-макетов

Порядок переноса:

1. Общий public layout: `index.html`, `auth.html`.
2. Student layout: nav, language switch, achievements modal.
3. Parent layout: dashboard, add child modal.
4. Admin layout: sidebar, cards, tables, actions.
5. Test player components: question nav, SCQ, MCQ, MATCHING, FILL_IN.
6. Result components: metrics, result nav, explanation box.
7. Quiz components.

Общие компоненты:

- `layout/base.html`
- `layout/student.html`
- `layout/parent.html`
- `layout/admin.html`
- `fragments/language-switch.html`
- `fragments/achievements-modal.html`
- `fragments/admin-sidebar.html`
- `fragments/alerts.html`
- `fragments/modal.html`
- `fragments/question-renderer.html`
- `fragments/pagination.html`

Правила:

- Не копировать одинаковую модалку достижений в каждую страницу.
- Не хранить правильные ответы в HTML.
- Не оставлять CDN как единственную зависимость для production.
- Все формы должны иметь server-side validation.

## 17. Тестовая стратегия

### Unit tests

- Validation DTO.
- Проверка ответов.
- Mastery formula.
- Streak rules.
- Push schedule validation.
- AI job state machine.

### Integration tests

- Repository + Flyway.
- Auth flow.
- Parent-child access control.
- Test session lifecycle.
- Admin content CRUD.
- Push scheduler.

### UI smoke tests

- Login.
- Student starts and finishes test.
- Parent opens child card.
- Admin creates question.
- Admin schedules push.

### Manual QA

- Mobile width 360px.
- Slow network simulation.
- RU/KK switch.
- Formula rendering.
- Refresh during test.
- Browser back button during test.
- AI generation failure.

### 2026-04-30 content-entry QA focus

- Use `docs/CONTENT_ENTRY_QA_PLAN.md` for the next manual pass.
- Primary flow: create topic -> open `/admin/questions/new` without query params -> select subject/grade/topic inside the form -> create and publish a question -> create and publish a lecture for the same topic.
- The question-create link from `/admin/questions` must not pre-bind subject/grade filters.
- SCQ/MCQ option deletion must be per-row with confirmation and backend soft-delete handling.
- This pass should record remaining gaps separately instead of expanding MVP scope during the smoke.

## 18. Риски и меры

### Риск: слишком широкий scope

Мера: MVP строить вокруг тестового цикла, а викторины, AI и predictive scoring включать по готовности ядра.

### Риск: качество AI-разборов

Мера: сначала шаблонные объяснения и human-reviewed мини-лекции, затем AI-разборы с логированием и ограничениями.

### Риск: потеря ответов в тесте

Мера: сохранять ответы на сервере после каждого изменения или батчем с локальным fallback.

### Риск: актуальность базы вопросов

Мера: content health dashboard, статусы модерации, version control вопросов.

### Риск: push по неправильной timezone

Мера: хранить время в UTC, показывать серверную timezone, явно конвертировать ввод оператора.

### Риск: сложность real-time Arena

Мера: не смешивать quiz real-time с основным тестовым движком; реализовать отдельным модулем после MVP.

## 19. Definition of Done для задач агентов

Каждая задача считается завершенной, если:

- Есть миграции БД, если менялась модель.
- Есть server-side validation.
- Есть тесты для бизнес-правил.
- UI использует общий layout и не дублирует крупные фрагменты.
- Ошибки показываются пользователю.
- Проверены права доступа.
- Обновлена документация API или DTO.
- Нет захардкоженных секретов.
- Не нарушены RU/KK и mobile требования.

## 20. Ревью плана и открытые решения

Требуют решения до или во время Этапа 0:

- Окончательный выбор легкого UI-подхода: Thymeleaf + Alpine или Thymeleaf + HTMX; отдельный SPA не рекомендуется для MVP.
- Production-канал push: Web Push, FCM для PWA, FCM/APNs через мобильное приложение.
- Точный список предметов для MVP.
- Точная шкала МОДО/СОР/СОЧ.
- Правила согласия родителей и обработки данных детей.
- Подтвердить договорные/юридические условия и лимиты хранения данных для OpenAI и DeepSeek перед включением real provider calls.
