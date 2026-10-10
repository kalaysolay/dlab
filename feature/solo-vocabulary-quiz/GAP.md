# Игровые викторины — GAP / журнал реализации

Последнее обновление: 2026-10-10. Ветка: `quiz-games-spec`.

## 1. Текущий этап

Этапы 1–5 завершены. ROOM-01..14, SEC-ROOM-01, TEST-ROOM-01 — DONE: универсальные комнаты, безопасные API/события, серверное расписание и защита STOMP. Финальная приёмка: 401 тест полного H2 suite / 70 классов и 155 целевых PostgreSQL / 18 классов; дополнительные 29 PostgreSQL после последней проверки expiry/start. Failures/errors/skipped=0; i18nVerify — 349 ключей RU/KK; пробный build с bootJar/jar успешен. Каталог выключен. Ученический UI этапа 6 реализован: UI-01..09 / TEST-UI-01 DONE. Финальные 413 H2 / 71 класс, 167 PostgreSQL / 19 классов и 23 Node/jsdom PASS; i18nVerify 424 ключа RU/KK, build/bootJar/jar PASS. UI-10, этап 6 и UX-03 остаются IN_PROGRESS только до ручного речевого прохода скринридером; этап 7 не начат.

Этот файл ведётся по запросу пользователя как оперативная сводка: сделано, осталось, проблемы, решения и результаты проверок. ID и детальные статусы задач синхронизируются с § 29 [спецификации](SPECIFICATION.md); GAP не объявляет этап выполненным отдельно от его критериев приёмки. [План](IMPLEMENTATION_PLAN.md) содержит порядок поставок и предложения контрактов, [use cases](USE_CASES.md) — пользовательскую приёмку.

## 2. Что сделано

| Дата | Результат | Подтверждение |
| --- | --- | --- |
| 2026-10-09 | Подготовлены спецификация v0.3, 37 use cases, план поставок и матрица требований | `SPECIFICATION.md`, `USE_CASES.md`, `IMPLEMENTATION_PLAN.md` |
| 2026-10-09 | Проверены ссылки, ID и зависимости трекера на момент завершения подготовки | 100 задач, все зависимости разрешимы; 18 пользовательских + 19 административных сценариев |
| 2026-10-09 | Создан GAP-файл и зафиксирована граница начала этапа 1 | Этот документ; `CFG-01` не зависит от незавершённого `ARCH-01` |
| 2026-10-09 | Пользователь явно принял пакет рекомендаций §§ 2.1/2.4 плана; закрыты входные решения и технически зафиксирован foundation | DEC-01..06, ARCH-01; [ARCHITECTURE.md](ARCHITECTURE.md) и § 25 спеки |
| 2026-10-09 | Подготовлены конфигурационный флаг и выключение в test-профиле, 6 лёгких проверок binding/YAML | `DamulabQuizProperties`, `DamulabQuizPropertiesTest`, application.yml/application-test.yml; итог запуска ниже |
| 2026-10-09 | Подготовлен первый добавочный блок V47: games/versions/subject config/types/grades, constraints и индексы | `V47__quiz_game_catalog_foundation.sql`; существующие rooms/rounds/answers и V1–V46 не изменены |
| 2026-10-09 | Добавлена быстрая schema-регрессия через Flyway/H2 и проверка флага в старом портале | `QuizGameFoundationSchemaTest`, `QuizArenaIntegrationTest`; это не PostgreSQL-проверка |
| 2026-10-09 | CFG-01 завершена; целевые проверки первого инкремента прошли | Gradle BUILD SUCCESSFUL: 6 flag + 12 schema/H2 + 7 legacy = 25, failures/errors 0 |
| 2026-10-10 | Добавлены V48/V49/V50, word/fact configs, аудитория, revision/idempotency, типизированные источники и снимки, skip/timeout и индексы | V1–V46 не изменены; nullable legacy-поля сохранены |
| 2026-10-10 | Реализованы enum/JPA банков и игр, расширены модели комнат и созданы целевые репозитории | Пакет kz.damulab.quiz.catalog; новые комментарии на русском |
| 2026-10-10 | PostgreSQL 16.15 подтвердил миграции с нуля, V46 → V50 и запуск приложения на старой истории | 44 теста: 18 schema + 10 repository + 2 migration + 1 upgrade startup + 7 legacy + 6 flag; 0 failures/errors |
| 2026-10-10 | Подготовлена воспроизводимая проверка и опциональные лимиты тестового JVM | VERIFY_STAGE1.md, scripts/quiz-foundation-test.init.gradle; глобальные настройки не изменены |
| 2026-10-10 | Финальный review и повторные проверки завершены; все задачи этапа 1 закрыты и синхронизированы со спекой/планом | Полный H2: 291 PASS, 3m 27s; PostgreSQL: 45 PASS, 1m 35s; i18nVerify: 321 ключ |
| 2026-10-10 | Добавлена V51 с неизменяемым журналом редакционных переходов и opt-in запретом самопроверки | `quiz_content_editorial_events`, `damulab.quiz.strict-self-review=false`; миграции H2/PostgreSQL PASS |
| 2026-10-10 | Реализованы сервисы версий/публикации слов, наборов и утверждений; новая редакция не меняет published pointer | `VocabularyAdminService`, `FactAdminService`, `EditorialWorkflowService`; аудит автора/проверяющего и архив без удаления |
| 2026-10-10 | Реализована общая диагностика дистракторов и управление запрещёнными парами | Альтернативы, аудитория всех классов, направления, cross-set opt-in, exclusions и нехватка трёх кандидатов покрыты тестами |
| 2026-10-10 | Реализованы реальные SSR-страницы банков на текущем admin-layout и относящаяся к ним часть UX-03 | `/admin/vocabulary`, `/admin/facts`, формы наборов/материалов, фильтры, bulk review; добавлены `flow/admin-fact-*` |
| 2026-10-10 | Этап 2 принят полным H2/PostgreSQL/security/i18n набором | H2 307 PASS; PostgreSQL 61 PASS (1m 4s); i18nVerify 323 ключа |
| 2026-10-10 | Добавлена V52: revision стабильной игры, GAME в редакционном журнале и отдельный append-only аудит видимости | V1–V51 не изменены; V51→V52 сохраняет прежние события; H2/PostgreSQL PASS |
| 2026-10-10 | Реализованы три typed configs игр, версии, review/approve/publish, архив и независимый hide/return | `QuizGameAdminService`, revision/pessimistic lock/REPEATABLE_READ; аудит и конкурентный save/publish PASS |
| 2026-10-10 | Реализованы общая диагностика и availability policy, реальные список/форма/content/checklist/preview | Пакетная выборка без N+1, все требуемые классы, shared distractors; `/admin/quiz-games/**`, ADMIN/CSRF/escaping PASS |
| 2026-10-10 | Выполнена административная часть UX-03 и браузерная приёмка | `flow/GAME_ADMIN_CONTRACT.md`, исправленные четыре макета; реальная false-игра прошла draft→review→approve→publish; wide/narrow |
| 2026-10-10 | Этап 3 принят окончательным H2/PostgreSQL/security/i18n набором | H2 332/65 классов PASS (2m 17s); PostgreSQL 86/13 PASS (1m 11s); i18nVerify 324 ключа |
| 2026-10-10 | Этап 4 принят: три провайдера, общий checker, атомарные снимки, безопасный reader, время/рейтинг и личный разбор; без новой миграции и комнатного API | ENG-01..09 / TEST-ENG-01 DONE; 371 H2 / 125 PostgreSQL, i18n 324, build PASS |

| 2026-10-10 | Этап 5 принят: идемпотентные комнаты, команды/аудитория/атомарный start, ответы/timeout/finish, безопасные DTO/события и STOMP; добавлена обоснованная V53, V1–V52 сохранены | ROOM-01..14 / SEC-ROOM-01 / TEST-ROOM-01 DONE; 401 H2 / 155 PostgreSQL + 29 финальных целевых, i18n 349, build PASS |

## 3. Статусы этапов 1–5 и что осталось дальше

| ID | Состояние | Что нужно получить / что мешает |
| --- | --- | --- |
| `CFG-01` | DONE | Типизированный флаг, default false, YAML/env override и 6 binding/resource тестов; 7 legacy-тестов прошли. Переключение каталога остаётся COMP-03 |
| `DB-01` | DONE | V47/V48/V49: игры/версии и все три typed configs с настоящими FK; H2/PostgreSQL PASS |
| `DB-02..06` | DONE | V48–V50: банки, room/round/answer extensions, индексы и ограничения; пустая БД/V46 на H2/PostgreSQL PASS |
| `DOM-01..03` | DONE | Enum/JPA, audit/коллекции, legacy startup/история, snapshot/terminal guards; H2/PostgreSQL PASS |
| `REP-01` | DONE | Публикации/пересечение аудитории/expiry/lock и пакетные запросы; каталог одним SQL, 11 repository tests PASS |
| `TEST-DB-01` | DONE | Пустая база, V46 с WAITING/ACTIVE/FINISHED/ответом/результатом, точное сравнение старых полей и Spring startup; H2/PostgreSQL PASS |
| `VOC-01..03` | DONE | Нормализация/дубли, версии/primary/alternatives и публикация с явной аудиторией; первая публикация пустого банка PASS |
| `VOC-04..05` | DONE | Общая policy дистракторов, exclusions/cross-set и реальные SSR-страницы/фильтры/bulk review; unit/repository/MVC PASS |
| `FACT-01..03` | DONE | Версии RU/KK, полноценный false, explanation/source/audience checks, список/форма и архив без удаления; PASS |
| `SEC-ADM-01` | DONE | `/admin/**` только ADMIN; CSRF для POST, изменяющих GET нет, все пользовательские тексты выводятся через escaped `th:text`/`th:field`; MVC PASS |
| `TEST-CONTENT-01` | DONE | Новый блок дал +16 тестов; полный H2 307, целевой PostgreSQL 61, failures/errors/skips 0; i18n 323 |
| `GAME-01..03` | DONE | Атомарные typed configs, отдельные версии, неизменность code/type после публикации и обе политики; H2/PostgreSQL PASS |
| `GAME-04..05` | DONE | Пакетная диагностика published→audience→playable, худший состав, minimum/error и явные warnings; review/audit/recheck POST PASS |
| `GAME-06..08` | DONE | Список/фильтры без N+1, реальная форма/content/checklist и безопасный preview; ADMIN/CSRF/escaping, detached MVC и браузер PASS |
| `GAME-09` | DONE | Независимый hide/return с аудитом и общая policy new/replay/pinned WAITING; интеграция комнатных HTTP-команд подтверждена этапом 5 |
| `TEST-GAME-01` | DONE | +25 тестов; финальные H2 332, PostgreSQL 86, failures/errors/skipped=0, i18n 324; V51→V52 и конкурентный save/publish PASS |

В этапах 1–5 незавершённых задач нет. Этап 4: `ENG-01..09`, `TEST-ENG-01` — DONE; этап 5: `ROOM-01..14`, `SEC-ROOM-01`, `TEST-ROOM-01` — DONE. Подробные подтверждения — §§ 29.7–29.8 спецификации и §§ 8–9 ниже. Дальше остаются:

- Этап 6: ученический каталог, комнаты, все форматы, результаты и клиент реализованы; итоговые статусы — § 10. Ручной проход скринридером открыт.
- UX-03 — банки и административные игры подготовлены; серверные cancel/expire реализованы, ученические экраны и полная a11y-приёмка остаются открытыми.
- Движок, комнатные команды/подписки, административная публикация и ученический путь реализованы; финальная a11y-приёмка этапа 6 ещё открыта.
- COMP-03/COMP-04 и QA-06 — переключение каталога, проверенный binary rollback и реальная копия рабочей БД перед запуском. В foundation проверена контролируемая V46-фикстура, не production-копия.

## 4. Проблемы и ограничения

| ID проблемы | Факт / влияние | Что нужно для снятия |
| --- | --- | --- |
| GAP-01 | Закрыто: пользователь принял пакет, нормы внесены в спеку, ARCHITECTURE.md фиксирует основу схемы и контрактов | DEC-01..06/ARCH-01 закрыты по решениям; кодовые DB/DOM статусы не закрыты автоматически |
| GAP-02 | Устранено для текущего запуска: найден JBR/JDK 21.0.9 с javac в IntelliJ. Java по-прежнему не настроена глобально | JAVA_HOME задаётся только в процессе Gradle: `C:/Program Files/JetBrains/IntelliJ IDEA Community Edition 2025.2.6.3/jbr`. Результат тестов записывается отдельно |
| GAP-03 | Закрыто для этапа 1: Docker 29.8.1, отдельный PostgreSQL 16.15, 45 тестов PASS. Контейнер dlab-quiz-stage1-pg остановлен, не удалён | Контролируемая V46-фикстура, не production-копия; реальные данные/объёмы — QA-06 |
| GAP-04 | Обойдена и повторно подтверждена: полный suite 291 тест PASS с opt-in лимитами после прежнего JVM native allocation failure | Gradle/test heap 256m, один worker, ActiveProcessorCount=2, Spring cache 2. Системная первопричина не доказана; это не диагноз аппаратной RAM. Глобальные настройки/чужие процессы не изменены |
| GAP-05 | Закрыто: foundation не хранила историю переходов, необходимую A-12/A-17 | Обоснованно добавлена только V51; V1–V50 не изменены. PostgreSQL миграция с нуля и V46→V51 PASS |
| GAP-06 | Закрыто: первый PostgreSQL-прогон после V51 выявил жёсткое ожидание числа 50/4 в migration-тесте | Ожидание синхронизировано с V51/5; повторный полный целевой набор 60 PASS. Данные/схема приложения не ослаблялись |
| GAP-07 | Закрыто: для конкурентной админки игр и A-19 не хватало revision/истории видимости | Обоснованно добавлена V52; V1–V51 сохранены, migrate/validate/upgrade и конкурентный тест PASS |
| GAP-08 | Закрыто: MVC-тесты с внешней транзакцией могли скрывать lazy-загрузку опубликованной версии при новом draft | `findAdminGame` загружает оба указателя; новый MVC-тест без внешней транзакции проверяет edit/content/publish; итоговые H2/PostgreSQL PASS |
| GAP-09 | Предметный legacy-банк не хранит явный universal или отдельный language | SUBJECT_QUIZ с UNIVERSAL получает понятную ошибку; доступны политики классов. Пустые gradeLinks не считаются универсальностью; оба текста RU/KK обязательны |

## 5. Принятые решения и открытые предложения

| Запись | Состояние / основание | Решение |
| --- | --- | --- |
| RUN-01 | Принято в рамках текущего запроса | «Первый этап» означает этап 1 плана; выполнять сначала независимую `CFG-01`, не обходить обязательный вход `ARCH-01` для миграций |
| RUN-02 | Требование спецификации, не новая продуктовая договорённость | Каталог по умолчанию выключен; существующие комнаты, маршруты и scheduler не меняются задачей флага |
| RUN-03 | Правила репозитория и сохранение пользовательских изменений | Gradle wrapper; не переписывать existing migrations и unrelated application.yml; не запускать destructive-команды/production-миграции |
| RUN-04 | Запрос пользователя | Обновлять GAP после каждого рабочего подхода: факты, подтверждения, остаток, проблемы и принятые решения. Не записывать секреты, реальные ответы учеников и предположения как факты |
| RUN-05 | Принято пользователем 2026-10-09, явный ответ на вопрос | Принят пакет §§ 2.1/2.4: hide/create/WAITING, settings flags/revision/ready reset, published bank at start, archive/start, общий content locale, конфигурируемая самопроверка, без автоматического удаления, fallback и safe rollback. Детали зафиксированы в ARCHITECTURE.md/спеке |
| RUN-06 | Техническое решение foundation после снятия GAP-01 | V47 добавляет ядро игр и subject config; word/fact configs — только после банков, с настоящими FK. Не создавать фиктивные источники/временно бесконтрольные ссылки и не закрывать DB-01 по одному V47 |
| RUN-07 | Реализованная техническая граница | Публикационная полнота/неизменность, ADMIN-права, providers/checkers, безопасные DTO и HTTP-команды — следующие этапы. Foundation не включает их обходом через CRUD |
| RUN-08 | Граница миграционной проверки | TEST-DB-01 — пустая БД и контролируемая существующая V46-схема/история + startup; реальная production-копия/объёмы — QA-06. Рабочая БД не изменялась |
| RUN-09 | Завершение тестового окружения | Проверены точный ID и label созданного контейнера; остановлен только dlab-quiz-stage1-pg. Данные/контейнер сохранены, Docker daemon и чужие контейнеры не остановлены |
| RUN-10 | Реализация этапа 2 по принятому контракту | Опубликованная версия не меняется; сохранение после публикации создаёт draft с новым version_no, публикация атомарно переключает pointer; архив меняет стабильный статус без удаления версий |
| RUN-11 | Обоснование V51 | Единственная новая миграция добавляет требуемую историю редакционных переходов; самопроверка использует существующую роль ADMIN и opt-in флаг, новых ролей нет |
| RUN-12 | Граница диагностики | `VocabularyDistractorService` — общий policy-компонент для админки и будущего provider; игровой движок и снимки раундов не реализованы |
| RUN-13 | Граница UX-03 | Выполнены только контракты банков этапа 2: факты, классы/универсальность, редакции и исключения; остальные экраны UX-03 не объявляются готовыми |
| RUN-14 | Реализация принятого контракта этапа 3 | Сохранение игры всегда создаёт отдельный draft; lock/revision защищают все изменения, code/type фиксируются первой публикацией, hide не создаёт учебную версию |
| RUN-15 | Техническая реализация безопасной диагностики | ROOM_GRADE проверяет каждый разрешённый класс; FIXED/PARTICIPANT — худшее допустимое сочетание до maxPlayers. Максимальные сочетания покрывают подмножества благодаря монотонному пересечению; будущий start обязан проверить фактический состав |
| RUN-16 | Граница этапа 3 | Availability policy проверена для новых комнат/replay/закреплённой WAITING, но новые HTTP-команды, provider и ученический каталог не добавлены. UX-03 расширена только административными играми |

## 6. Проверки текущего подхода

- Обнаружена ветка `quiz-games-spec`; сохранены существующие изменения документации.
- Проверены правила проекта, текущие `QuizPageController`, `QuizTimeoutScheduler`, конфигурация и `QuizArenaIntegrationTest`.
- Первый запуск `gradlew.bat test --tests kz.damulab.config.DamulabQuizPropertiesTest --tests kz.damulab.QuizArenaIntegrationTest --console=plain`: compileJava/compileTestJava прошли, test не завершился из-за native memory failure. Wrapper скачал Gradle 8.11.1.
- Повтор с лимитами выполнил 25 тестов: один schema-тест упал на фикстуре subjects без обязательных description_ru/description_kk из V30. Исправлена только фикстура, не V30/новая схема.
- Итоговый запуск тех же трёх классов: `--no-daemon --max-workers=1`, `-Dorg.gradle.jvmargs=-Xmx256m -Xms64m -XX:MaxMetaspaceSize=192m -XX:ActiveProcessorCount=2 -XX:ReservedCodeCacheSize=64m`, `-I .run-logs/quiz-stage1-test.init.gradle` — BUILD SUCCESSFUL (38 s). 6 + 12 + 7 = 25 тестов, 0 failures, 0 errors. Полный suite этим запуском не заявляется.
- Локальный init-script (ignored .run-logs) задаёт Test minHeapSize=64m, maxHeapSize=256m, maxParallelForks=1 и JVM ActiveProcessorCount=2/ReservedCodeCacheSize=64m/Xss512k. На другой машине можно повторить обычную команду трёх классов или создать такой же init-script; build.gradle не изменён.
- Отчёты: [флаг](../../build/test-results/test/TEST-kz.damulab.config.DamulabQuizPropertiesTest.xml), [V47 / H2](../../build/test-results/test/TEST-kz.damulab.QuizGameFoundationSchemaTest.xml), [legacy](../../build/test-results/test/TEST-kz.damulab.QuizArenaIntegrationTest.xml). Это локальные build-артефакты, не содержимое репозитория.
- Предыдущий подход: docker info сообщал отсутствие engine pipe. В текущем подходе пользователь запустил Docker; тестовый контейнер создан отдельно, рабочие БД не затронуты.
- 2026-10-10: первые расширенные H2 schema/repository проверки — 28 PASS; старый QuizArenaIntegrationTest также прошёл после добавления V48–V50 и JPA.
- 2026-10-10: PostgreSQL сначала 43 PASS, затем 44 PASS с QuizFoundationUpgradeStartupTest. V1–V50, повторный migrate, validate, V46-фикстура с точным сравнением старых полей и Spring startup/чтение истории подтверждены.
- PostgreSQL-отчёты сохранены в ignored .run-logs/quiz-stage1-pg-reports; основной build/test-results/test будет заменён полным H2-прогоном. Подробные команды — VERIFY_STAGE1.md.
- Первый полный прогон H2: 290 тестов / 58 классов, failures/errors/skipped = 0; i18nVerify — 321 ключ RU/KK, BUILD SUCCESSFUL (3m 34s). Отчёты сохранены в ignored .run-logs/quiz-stage1-h2-reports.
- Финальный review: snapshot сначала валидирует все поля, чтобы не оставлять частичный объект после исключения; CANCELLED/EXPIRED нельзя превратить в FINISHED. Добавлен тест восстановления после неполного снимка; schema-каталог явно проверяет status/published_at самой версии.
- Финальный PostgreSQL-прогон с version-controlled init-script: 45 тестов (18 schema + 11 repository + 2 migration + 1 upgrade startup + 7 legacy + 6 flag), failures/errors/skipped 0, BUILD SUCCESSFUL (1m 35s). Отчёты в .run-logs/quiz-stage1-pg-reports.
- Финальный полный H2-прогон окончательного кода: 291 тест / 58 классов, failures/errors/skipped 0, BUILD SUCCESSFUL (3m 27s). i18nVerify: 321 совпадающий ключ RU/KK. Отчёты в build/test-results/test, build/reports/tests/test и копии .run-logs/quiz-stage1-h2-reports.
- Этап 2, PostgreSQL 16.15: после проверки label `codex.task=quiz-games-stage1` использован только `dlab-quiz-stage1-pg`, порт 56094; 61 тест / 10 классов, failures/errors/skipped 0, BUILD SUCCESSFUL (1m 4s). V1→V51, V46→V51 и startup PASS.
- Этап 2, финальный полный H2 suite: 307 тестов / 62 класса, failures/errors/skipped 0, BUILD SUCCESSFUL; `i18nVerify`: 323 ключа RU/KK.
- git diff --check успешен; branch quiz-games-spec. Изменения не коммитились/не отправлялись; пользовательские изменения и старые V1–V46 сохранены.
- Этап 3: V1→V52, V46→V52/startup и отдельная V51→V52 проверены на H2/PostgreSQL; прошлые материалы и журнал банков сохранены.
- Финальный H2: 332 теста / 65 классов, failures/errors/skipped=0, BUILD SUCCESSFUL (2m 17s). Финальный PostgreSQL 16.15: 86 тестов / 13 классов, failures/errors/skipped=0, BUILD SUCCESSFUL (1m 11s). `i18nVerify`: 324 ключа RU/KK. Отчёты в ignored `.run-logs/quiz-stage3-h2-reports` и `.run-logs/quiz-stage3-pg-reports`; команды — VERIFY_STAGE1.md.
- Этап 3 дал +25 проверок: 16 service/integration, 7 MVC, 1 конкурентная, 1 migration. Покрыты три типа, источники/аудитории/дистракторы, неполные публикации и независимый draft, warning confirmation, code/type, повторная публикация, архив/hide/return, stale revision и bounded SQL. MVC дополнительно выполняется без внешней service-транзакции.
- Browser smoke: только test-профиль и отдельная H2 in-memory на 127.0.0.1:18083, флаг каталога/worker false; синтетические fact set/false fact/game прошли реальные review/approve/publish. Проверены narrow/wide layout и выбор только соответствующей типу секции. Приложение остановлено, listener отсутствует; временная вкладка закрыта.
- Перед PostgreSQL повторно проверены имя/label `codex.task=quiz-games-stage1`, фактический порт 52848. После финального прогона остановлен только `dlab-quiz-stage1-pg`, контейнер/данные сохранены. Рабочая/production-БД не затронута; коммитов и отправки нет.

## 7. Следующий шаг

Этап 5 завершён. Следующий этап — 6: ученический каталог, комната, игровые оболочки всех форматов, результаты, reconnect/polling и RU/KK/a11y-приёмка. Он не начат. Каталог остаётся выключенным. UX-03 целиком остаётся IN_PROGRESS; QA-06, COMP-04 и запуск MVP открыты.

## 8. Рабочий журнал этапа 4

- Ветка `quiz-games-spec` и исходное рабочее дерево проверены. Изменения этапов 1–3 и пользовательские изменения сохранены. Новых миграций нет, V1–V52 не изменялись.
- Добавлены общий QuizRoundProvider, Subject/Vocabulary/Fact providers, атомарный QuizRoundFactory, фактическая аудитория, снимки и общие checker. Слова/факты не создают QuestionVersion. SUBJECT + UNIVERSAL явно запрещён.
- Общая VocabularyDistractorService усилена проверкой альтернатив обоих слов и проверенной публикации каждого набора-кандидата; primary-публикация качественного слова по-прежнему не зависит от трёх дистракторов. Диагностика и игровой provider используют одну policy.
- Добавлены безопасный reader текущего окна, evaluator ответов/skip/timeout и результат/личный разбор по снимкам. Скорость не даёт баллов, ранний skip учитывается полным лимитом. Финализация lifecycle, команды HTTP/STOMP/scheduler и UI не добавлены.
- Контракты JSON, пересечения классов, транзакции, payload и персональной выдачи описаны в § 9 ARCHITECTURE.md; комментарии нового кода — на русском.
- Целевой H2 сначала выявил три ошибки синтетической фикстуры с отсутствующим 6-м классом. Исправлено на существующие 4-й/5-й без изменения policy; повтор PASS. Фикстура malformed question изолирована собственным предметом для повторных PostgreSQL-прогонов; тест будущего текста использует разные уникальные условия.
- Первый PostgreSQL: 125 тестов / 16 классов, failures/errors/skipped=0, BUILD SUCCESSFUL (1m 40s). Label/имя проверены перед start/stop, порт 60221; контейнер остановлен и сохранён. Отчёты `.run-logs/quiz-stage4-pg-reports`.
- Первый полный H2 и пробный `build`: 371 тест / 68 классов, failures/errors/skipped=0; bootJar/jar собраны, BUILD SUCCESSFUL (2m 37s), i18nVerify — 324 ключа RU/KK. Отчёты `.run-logs/quiz-stage4-h2-reports`. Финальная версия усиленных тестов проверяется повторно; DONE ещё не объявлен.
- Повтор PostgreSQL выявил влияние committed rollback-фикстур на две глобальные foundation-проверки (каталог и expiry). Добавлено завершение только синтетических комнат `@engine.test` и архив их игр через существующий admin service в REQUIRES_NEW после теста; история не удаляется. Перед итоговым повтором engine-набор выполняет эту очистку состояния прежних собственных фикстур. Предикаты/ожидания foundation и доменные ограничения не ослаблены.
- Финальный PostgreSQL: **125 / 16 классов**, failures/errors/skipped=0, BUILD SUCCESSFUL (1m 19s). Финальный полный H2: **371 / 68 классов**, failures/errors/skipped=0, `build i18nVerify --rerun-tasks` BUILD SUCCESSFUL (2m 34s); **324 ключа**, bootJar/jar собраны. +39 новых проверок: 17 integration, 17 checker/results-контрактов и 5 словарных контрактов.
- Финальные отчёты: `.run-logs/quiz-stage4-pg-reports`, `.run-logs/quiz-stage4-h2-reports`, текущие `build/test-results/test` и `build/reports/tests/test`. Команды/изоляция — VERIFY_STAGE1.md. Перед последним stop повторно проверены имя/label, порт был 62785; `dlab-quiz-stage1-pg` остановлен и сохранён. Коммитов/отправки/изменений рабочей БД нет, V1–V52 сохранены, каталог false. ENG-01..09 / TEST-ENG-01 синхронизированы с § 29.7 как DONE; UX-03 остаётся IN_PROGRESS, этап 5 не начат.

## 9. Этап 5 — универсальная комната (2026-10-10)

Пользователь разрешил переход к этапу 5 после проверки входных условий. Этап 4 принят, новых продуктовых блокеров нет. По итогам приёмки ROOM-01..14, SEC-ROOM-01, TEST-ROOM-01 — DONE; этап 6 не начат, UX-03 остаётся IN_PROGRESS, каталог выключен. Ниже сохранён журнал промежуточных проверок и исправлений.

Последовательность: транзакционные команды и идемпотентный create → переходный HTTP и безопасные DTO → scheduler/after-commit события и авторизация доставки STOMP → проверки lifecycle/гонок/безопасности на H2 и изолированном PostgreSQL → полный suite, i18nVerify, пробный build и синхронизация трекера.

Первоначальная попытка lock-протокола с no-op UPDATE отклонена по результатам конкурентных тестов. Текущий протокол: REPEATABLE_READ + ожидающий PESSIMISTIC_WRITE, затем FORCE_INCREMENT/@Version command_revision (V53). Child-only команды защищены от устаревшего snapshot; settings_revision меняется только при существенных настройках. Повтор начинается в новой транзакции, коллизия create откатывает room/host/key целиком. V1–V52 сохранены.

Промежуточный H2-прогон новых комнат, событий, миграции, конфигурации и legacy: 46 тестов PASS. Первое конкурентное испытание показало, что no-op UPDATE родителя недостаточен для snapshot isolation H2: два join могли видеть прежний состав, ответы сталкивались по unique. Подход заменён на V53 command_revision + JPA @Version/PESSIMISTIC_FORCE_INCREMENT, отдельный от settings_revision. Повторяются optimistic/serialization failures в новой транзакции; последнее место, первый ответ и повторный start теперь проходят. V1–V52 не менялись. Начальный settingsRevision=0; отсутствующие настройки в settings сохраняют прежние значения.

Приёмка включает принудительный unique INSERT collision двух create после обоих exists=false, rollback после пакетного INSERT раундов, start/start, join/join, start/join, cancel/start, expire/start, settings/ready, settings/start, answer/answer и answer/timeout. Проверены реальный simple broker inbound/outbound, multiple tabs/reconnect/leave-delivery, CSRF/роль/same-origin, canonical retry, false/skip/timeout и все четыре предметных формата/слова/факты. На этом промежуточном шаге финальные PostgreSQL/H2/build ещё выполнялись; итог приведён ниже.

Первый полный PostgreSQL-прогон показал NOWAIT у прямого Hibernate FORCE_INCREMENT (55P03); retry мог исчерпаться до commit конкурента. Сервис теперь сначала ждёт PESSIMISTIC_WRITE, затем увеличивает версию уже своей строки. Целевые 25 room + 3 event на PostgreSQL прошли. Полный повтор выявил только загрязнение cross-set диагностического теста опубликованными synthetic word-set предыдущего room прогона. Проверка банка не ослаблялась: cleanup собственных @room.test фикстур теперь архивирует также их банки через редакционные сервисы, сохраняя историю/FK. Старые синтетические фикстуры того же набора закрываются этим же способом; чужие данные не затрагиваются. Финальный повтор выполняется после cleanup.

Финальная приёмка этапа 5:
- PostgreSQL: 155 тестов / 18 классов, failures/errors/skipped=0, BUILD SUCCESSFUL (1m 46s); положительный HTTP create/retry дополнительно 1 PASS. После последнего усиления expiry/start — 25 room + 4 event = 29 PASS на PostgreSQL. Истечение во время формирования batch полностью откатывает start и снимки.
- Финальный полный H2: 401 тест / 70 классов, failures/errors/skipped=0; build i18nVerify --rerun-tasks BUILD SUCCESSFUL (3m 15s), bootJar/jar собраны, 349 ключей RU/KK. +30 проверок к этапу 4; legacy-регрессия и V46→V53/startup PASS.
- Отчёты: .run-logs/quiz-stage5-pg-reports, .run-logs/quiz-stage5-pg-http-reports, .run-logs/quiz-stage5-pg-final-room-reports, .run-logs/quiz-stage5-h2-reports. Команды и изоляция — VERIFY_STAGE1.md.
- Последний порт 64652 получен через docker port после проверки точного имени/label. Перед stop имя и codex.task=quiz-games-stage1 повторно проверены; dlab-quiz-stage1-pg остановлен, не удалён. Рабочая/production-БД и чужие контейнеры не затрагивались.
- Добавлена только обоснованная V53 command_revision, V1–V52 не изменены. Сохранены все исходные незакоммиченные изменения; коммитов/отправки нет. Каталог false, этап 6 не начат, UX-03 IN_PROGRESS. Распределённый broker не входит в текущую поставку; защита проверена для существующего simple broker в одном процессе. Статусы синхронизированы с § 29.8.

## 10. Этап 6 — ученический интерфейс

Пользователь разрешил этап 6. UI-01..09 / TEST-UI-01 — DONE; UI-10 — IN_PROGRESS. Выполненная последовательность:
пакетный безопасный каталог → SSR-карточки/create/preview → общий host/guest экран →
виджеты всех форматов и личный результат → связь/таймер/a11y → MVC/клиентские проверки,
изолированный PostgreSQL, браузер 360 px/desktop, полный H2, i18nVerify и пробный build.
Флаг остаётся default false: новый вход подготовлен для проверки с opt-in, legacy вход
сохранён; ранее созданные новые комнаты доступны независимо от флага. Это необходимое
подключение UI, приёмка COMP-03/rollback/стартового контента этапа 7 не объявляется.
Все исходные изменения сохранены. Миграции и рабочая БД не меняются, коммитов нет.

Реализовано:
- Пакетный безопасный QuizStudentCatalog с общими diagnostics/availability/audience,
  сортировкой, опубликованными версиями, явной аудиторией, доступными классами и блокерами.
- Thymeleaf-карточки, CSRF POST/create/retry, preview/join, host/guest, pinned settings,
  ready/start/solo, invite/code, cancel/leave. Флаг false сохраняет legacy вход.
- Общий safe клиент: SCQ/MCQ/MATCHING/FILL_IN, четыре перевода и boolean false;
  серверный таймер, необратимый ответ, skip, polling/STOMP/reload и canonical offline retry.
- Рейтинг, процент, время без speed bonus, личный разбор и replay через новую комнату;
  безопасное экранирование, без будущих вопросов и преждевременных ключей/объяснений.
- RU/KK, app-shell/CSS, 360 px/44×44, focus/aria-describedby, reduced motion,
  пороги 10/5 с подавлением повторов в sessionStorage.

Проверки 2026-10-10:
- 23 Node/jsdom PASS; все typed форматы, false, CSRF, фокус, блокировка, safe text,
  offline retry, poll/revision, приглашение и cancelled/expired.
- Предварительный H2: 12 UI MVC/integration + 7 legacy PASS. Итог полного H2/build приведён ниже.
- PostgreSQL: 167 тестов / 19 классов, failures/errors/skipped=0, BUILD SUCCESSFUL
  (3m 20s). Точный /dlab-quiz-stage1-pg и label codex.task=quiz-games-stage1 проверены
  перед start/stop; новый порт 54627 получен docker port. Контейнер остановлен,
  не удалён; отчёты .run-logs/quiz-stage6-pg-reports. Рабочая БД не затрагивалась.
- Реальный браузер: solo из двух действий, две независимые host/guest сессии,
  RU/KK, все форматы, false/skip, собственный результат/replay, reload, cancel/expiry.
  360×800: нет горизонтального overflow, цель выбора 60 px, submit/skip 44 px.
  Снимок .run-logs/quiz-stage6-mobile-kk.png. В браузере найден и исправлен toggle
  приглашения; отдельный DOM regression не допускает возврат этой ошибки.

Осталось: ручной речевой проход настоящим скринридером согласно поставке 6 плана;
AX-структура, фокус и keyboard-проверки не объявляются его заменой. UI-10/UX-03
сохраняют IN_PROGRESS, весь этап 6 не объявляется DONE. Итоговые метрики полного H2/build приведены ниже. Этап 7 не начат, новых миграций/коммитов нет.

Финальная автоматическая приёмка:
- Полный H2 suite: 413 тестов / 71 класс, failures/errors/skipped=0.
  build i18nVerify --rerun-tasks — BUILD SUCCESSFUL (4m 38s), 424 совпадающих ключа
  RU/KK; bootJar/jar собраны. PostgreSQL 167/19 и Node/jsdom 23/23 также PASS.
- Отчёты .run-logs/quiz-stage6-h2-reports/test и html проверены по XML;
  PostgreSQL .run-logs/quiz-stage6-pg-reports/test и html сохранены отдельно.
- Последняя упаковка обновляет только CSS целей ссылок 44×44 и русские комментарии;
  игровая логика после полного suite не менялась. JS syntax и diff --check PASS.
- DONE: UI-01..09 и TEST-UI-01. Единственный незавершённый критерий UI-10 —
  речевой проход настоящим скринридером (IMPLEMENTATION_PLAN.md, поставка 6).
  Этап 6 и UX-03 IN_PROGRESS; утверждать полное завершение этапа пока нельзя.
- Каталог false, этап 7 не начат, миграций этапа 6 нет; V1–V53 сохранены.
  Контейнер остановлен/сохранён, рабочая БД и чужие процессы не затрагивались.
  Все исходные незакоммиченные изменения сохранены; коммитов/отправки нет.

Заключительная упаковка assemble i18nVerify — BUILD SUCCESSFUL (26s), 424 ключа;
оба JAR обновлены с последними CSS и комментариями. Дополнительных изменений логики нет.
