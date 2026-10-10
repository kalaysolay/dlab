# Проверка этапа 1: схема и доменная основа

Эти команды выполняют тесты, а не миграцию рабочей базы. Нужен JDK 21 с javac.
В текущей среде JAVA_HOME задавался только в процессе PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.3\jbr'
```

## Полный набор тестов на H2

Из корня репозитория. QUIZ_TEST_DB_* должны отсутствовать: test-профиль по умолчанию
использует H2 в памяти. Если переменные уже выставлены, очистить их только в текущем процессе:

```powershell
$env:QUIZ_TEST_DB_URL = $null
$env:QUIZ_TEST_DB_DRIVER = $null
$env:QUIZ_TEST_DB_USER = $null
$env:QUIZ_TEST_DB_PASSWORD = $null
.\gradlew.bat test i18nVerify --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx256m -Xms64m -XX:MaxMetaspaceSize=192m -XX:ActiveProcessorCount=2 -XX:ReservedCodeCacheSize=64m' -I scripts/quiz-foundation-test.init.gradle --console=plain --rerun-tasks
```

Init-script — опциональное ограничение памяти/параллелизма и Spring context cache
только для тестового JVM. На машине без ограничения памяти допустим обычный gradlew test.
Он не меняет настройки приложения, ОС или глобальные переменные.

## Целевой набор на PostgreSQL

Использовать только отдельную пустую тестовую БД, **не production и не локальную рабочую БД**.
Тесты запускают Flyway и seed приложения; migration/startup-тесты дополнительно создают
свои случайные схемы. Они не запускают Flyway clean, repair или down-migration.

Пример создания отдельного контейнера (имя должно быть свободно; существующий не удалять):

```powershell
docker run --detach --name dlab-quiz-stage1-pg --label codex.task=quiz-games-stage1 --memory=512m --cpus=2 --env POSTGRES_USER=quiz_test --env POSTGRES_PASSWORD=quiz_test_local --env POSTGRES_DB=quiz_test --publish 127.0.0.1::5432 postgres:16-alpine
docker port dlab-quiz-stage1-pg
```

Подставить фактически выданный порт, после готовности PostgreSQL:

```powershell
$env:QUIZ_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:PORT/quiz_test'
$env:QUIZ_TEST_DB_DRIVER = 'org.postgresql.Driver'
$env:QUIZ_TEST_DB_USER = 'quiz_test'
$env:QUIZ_TEST_DB_PASSWORD = 'quiz_test_local'
.\gradlew.bat test --tests kz.damulab.QuizFoundationMigrationTest --tests kz.damulab.QuizFoundationRepositoryTest --tests kz.damulab.QuizFoundationUpgradeStartupTest --tests kz.damulab.QuizGameFoundationSchemaTest --tests kz.damulab.QuizArenaIntegrationTest --tests kz.damulab.config.DamulabQuizPropertiesTest --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx256m -Xms64m -XX:MaxMetaspaceSize=192m -XX:ActiveProcessorCount=2 -XX:ReservedCodeCacheSize=64m' -I scripts/quiz-foundation-test.init.gradle --console=plain --rerun-tasks
```

Учётные данные в примере синтетические и только для loopback-контейнера.
После прогона очистить четыре process env-переменные и остановить созданный тестовый контейнер,
сначала проверив его имя и label. Удаление контейнера/volume не требуется для тестов.

Контейнер текущего подхода остановлен, не удалён. Для повторной проверки можно выполнить
docker start dlab-quiz-stage1-pg и снова уточнить порт через docker port вместо нового docker run.

## Что подтверждает набор

| Проверка | Подтверждение |
| --- | --- |
| Пустая база | Текущие V1–V52 проходят; повторный migrate не применяет ничего; validate успешен |
| Существующая V46 | Создаются WAITING/ACTIVE/FINISHED, участники, предметные раунды, ответ и результат; после V47–V52 все прежние столбцы и значения сравниваются без изменений |
| Запуск поверх V46 | Spring сам применяет V47–V52, Hibernate validate проверяет JPA; приложение читает прежнюю историю и сохраняет флаг выключенным |
| DB ограничения | Владельцы published/draft, типовые configs/направления, максимум один primary, дубли нормализованных переводов, единственный источник раунда, restrict истории, revision/idempotency/skip/timeout |
| JPA/репозитории | Аудит и коллекции сохраняются; false не теряется; аудитория — пересечение классов, пустая не universal; архивы/черновики исключены; версии карточек одним SQL; варианты и исключения загружаются пачкой |
| Legacy | 7 существующих QuizArenaIntegrationTest проходят; время старых ответов не выдумывается |

Контролируемая V46-фикстура — не копия production. Проверка реальной копии БД
с рабочими объёмами/особенностями остаётся задачей QA-06 перед запуском функции.
Временные PostgreSQL-схемы содержат только синтетические данные.

Отчёты последнего Gradle-прогона: build/reports/tests/test/index.html и build/test-results/test/.
При следующем прогоне Gradle заменяет их; локальные PostgreSQL-отчёты текущего подхода
сохранены отдельно в ignored .run-logs/quiz-stage1-pg-reports. Итоговые числа и проблемы — GAP.md.

После этапа 2 к целевому PostgreSQL-набору добавляются `--tests kz.damulab.quiz.catalog.*`.
Финальный результат 2026-10-10: 61 тест PostgreSQL и 307 тестов полного H2 suite,
ошибок и пропусков нет; `i18nVerify` — 323 ключа. V51 содержит только редакционный audit.

## Этап 3: административные игры

Тот же `--tests kz.damulab.quiz.catalog.*` включает три новых класса: `QuizGameAdminIntegrationTest`,
`QuizGameAdminMvcTest`, `QuizGameConcurrencyTest`. Migration suite дополнительно проверяет V51→V52:
сохранение прежнего журнала банков, начальную revision=0 и добавление аудита GAME/видимости.
Для смены H2/PostgreSQL обязательно принудительно выполнить test: используйте `test --rerun`
или прежний `--rerun-tasks`; process env не следует считать гарантированным ключом Gradle up-to-date.

Проверяются три типизированных источника, версия/код/тип, обе политики, наборы классов всех игроков,
нехватка дистракторов, опубликованный повреждённый состав и отдельные неполные черновики,
warning confirmation, повторная проверка POST, source archive, hide/return/history, stale revision,
конкурентное save/publish и ограниченное число SQL списка. MVC проходит реальные Thymeleaf-формы,
ADMIN/STUDENT/PARENT/anonymous, CSRF, отсутствие изменяющих GET и безопасный предпросмотр.
Новые комнатные HTTP-команды не входят в этап 3; контракты create/replay/WAITING проверяются общей policy.

Финальный полный H2 2026-10-10: 332 теста / 65 классов, failures/errors/skipped=0,
BUILD SUCCESSFUL (2m 17s), `i18nVerify` — 324 ключа RU/KK. Целевой PostgreSQL: 86 тестов / 13 классов,
failures/errors/skipped=0, BUILD SUCCESSFUL (1m 11s). Последний MVC-тест выполняется без внешней
транзакции и проверяет edit/content/publish после новой редакции опубликованной игры.
Локальные отчёты сохраняются в ignored `.run-logs/quiz-stage3-h2-reports` и `.run-logs/quiz-stage3-pg-reports`.
Браузерный smoke использует только loopback 127.0.0.1:18083, test-профиль и отдельную H2 in-memory,
каталог/timeout worker выключены. Созданы лишь синтетические набор, false-утверждение и игра;
через реальные страницы выполнен draft→review→approve→publish. Проверены narrow/wide layout
и переключение типоспецифичной секции. После проверки приложение остановлено, временная вкладка закрыта.
Контейнер `dlab-quiz-stage1-pg` использовал фактический порт 52848; label повторно проверен,
контейнер остановлен, не удалён. Рабочая/production-БД не менялась.

## Этап 4: движок, снимки и результаты

К прежнему целевому набору PostgreSQL добавлен `--tests kz.damulab.quiz.engine.*`;
`--tests kz.damulab.quiz.catalog.*` уже включает `QuizEngineIntegrationTest`.
Новых миграций нет; V1–V52 не изменялись. Фабрика/провайдеры и DTO проверяются напрямую
через серверные компоненты: новые HTTP-команды, STOMP/scheduler и ученический UI остаются этапом 5–6.

Новые классы проверок:

- `QuizEngineContractTest`: четыре предметных формата и прежние режимы FILL_IN, перевод,
  true/false/отсутствие ответа, канонический retry, серверное окно `[start,end)`, skip/timeout,
  один балл без бонуса скорости, полный лимит в ничьей, joinedAt, процент и nullable legacy-время.
- `VocabularyProviderContractTest`: альтернативы обоих слов, primary/дубли, все классы,
  направление, симметричный запрет, explicit cross-set opt-in и фиксированный shuffle.
- `QuizEngineIntegrationTest`: три провайдера, уникальные источники/общий порядок,
  аудитория фактических профилей, текущая публикация и архив, недостаточность,
  malformed-снимок и rollback общей транзакции после пакетного сохранения,
  сериализация текущего DTO без секретов/будущих условий, FINISHED/membership/личный разбор,
  идемпотентные результаты и сохранение истории после изменения/переиздания/архива банков.

При повторе сначала проверить точное имя и label контейнера, затем получить порт заново:

```powershell
docker inspect --format '{{.Name}} {{index .Config.Labels "codex.task"}} {{.State.Status}}' dlab-quiz-stage1-pg
# Ожидается /dlab-quiz-stage1-pg и quiz-games-stage1. Только затем:
docker start dlab-quiz-stage1-pg
docker port dlab-quiz-stage1-pg 5432/tcp
docker exec dlab-quiz-stage1-pg pg_isready -U quiz_test -d quiz_test
```

Использовать синтетические process env из раздела PostgreSQL, с фактическим портом.
Повторить прежнюю Gradle-команду с `--tests kz.damulab.quiz.catalog.* --tests kz.damulab.quiz.engine.*`.
После прогона повторно проверить label и остановить только этот контейнер, не удаляя его.
Отчёты PostgreSQL сохраняются отдельно перед сменой БД. Для полной H2-приёмки и пробной сборки
очистить четыре QUIZ_TEST_DB_* process env и выполнить:

```powershell
.\gradlew.bat build i18nVerify --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx256m -Xms64m -XX:MaxMetaspaceSize=192m -XX:ActiveProcessorCount=2 -XX:ReservedCodeCacheSize=64m' -I scripts/quiz-foundation-test.init.gradle --console=plain --rerun-tasks
```

Не выполнять принудительную перекомпиляцию одновременно с bootRun этого проекта.
Проверка 2026-10-10: PostgreSQL **125 тестов / 16 классов**, failures/errors/skipped=0,
BUILD SUCCESSFUL (1m 40s). Перед start/stop проверены точное имя и `codex.task=quiz-games-stage1`;
фактический порт был **60221**. Контейнер остановлен, не удалён.
Отчёты — ignored `.run-logs/quiz-stage4-pg-reports`.

Финальный повтор PostgreSQL после усиления тестов: **125 / 16**, failures/errors/skipped=0,
BUILD SUCCESSFUL (1m 19s), фактический порт **62785**. Перед stop повторно проверен label;
контейнер остановлен и сохранён. Для воспроизводимости committed фикстуры тестов реального rollback
после проверки отменяют только свои WAITING-комнаты (`@engine.test`) и архивируют свои игры через
существующий `QuizGameAdminService` в REQUIRES_NEW. Строки/банки/история не удаляются;
прежние глобальные проверки catalog/expiry не ослаблялись. Собственный предмет malformed-фикстуры
не смешивается с контентом других тестов. Первое повторное выполнение обнаружило влияние старых
fixture-строк на два foundation-теста; после исправления изоляции прошли engine-набор и весь целевой набор.

Заключительный полный H2 suite и пробный build финального кода: **371 тест / 68 классов**,
failures/errors/skipped=0; BUILD SUCCESSFUL (2m 34s), `i18nVerify` — **324 ключа RU/KK**.
`bootJar` и `jar` собраны. Итоговые отчёты — ignored `.run-logs/quiz-stage4-h2-reports`
и текущие `build/reports/tests/test`, `build/test-results/test`.
Этап 4 принят; этап 5 и ученический UI не начаты, каталог false, UX-03 остаётся IN_PROGRESS.

## Этап 5: комнаты, API, scheduler и подписки

К целевому PostgreSQL-набору уже подходят маски `kz.damulab.quiz.catalog.*` и
`kz.damulab.quiz.engine.*`: в них добавлены QuizRoomIntegrationTest (25 исполнений)
и QuizRoomEventContractTest (4). В DamulabQuizPropertiesTest добавлена проверка
конфигурируемого положительного waiting-timeout. Миграция V53 добавляет техническую
command_revision; V1–V52 не изменяются. Старые поля/история проверяются при V46→current,
проверены default/check и повторная миграция, приложение стартует поверх legacy.

Новые integration fixtures коммитятся для проверки настоящих гонок/rollback и имеют
уникальные email `@room.test`. Cleanup сохраняет комнаты/версии/ответы/результаты:
собственные WAITING отменяются, собственные игры и банки архивируются редакционными
сервисами. Это исключает загрязнение глобальной cross-set диагностики при повторном
прогоне того же изолированного PostgreSQL. Существующие проверки не ослабляются.

Проверяются: create/retry/conflict и реальные unique collision; solo/1–4/max;
preview/member/role/CSRF и безопасная сериализация; все предметные форматы/word/fact;
revision/ready/settings, аудитория и rollback; leave/cancel/expiry; серверные окна,
false/skip/timeout, finish/restart recovery и актуальная версия replay;
start/start, последний-slot join/join, start/join, cancel/start, expire/start,
settings/ready, settings/start, answer/answer, answer/timeout и двойной finish.
STOMP: exact topic, аутентификация/STUDENT, outsider/parent/admin/wildcard/SEND,
несколько вкладок, reconnect после leave, queued delivery и гонка leave/handler;
дополнительно реальный simple broker inbound/outbound и same-origin HTTP handshake.
События выходят после commit, rollback молчит; переход раунда сигнализируется даже
когда все успели ответить. Каталог остаётся false; экранов этапа 6 в приёмке нет.

Команды и memory profile прежние. Перед стартом проверять точное имя/label контейнера,
получать порт заново; при смене БД принудительно запускать test. После PostgreSQL
сохранить отчёты отдельно, повторно проверить label и остановить только свой контейнер.
Для финальной H2-приёмки очистить QUIZ_TEST_DB_* и выполнить полный `build i18nVerify`
с `--rerun-tasks`, убедившись, что bootRun dlab не работает.

PostgreSQL финального серверного кода: 155 тестов / 18 классов, failures/errors/skipped=0,
BUILD SUCCESSFUL (1m 46s). Порт 62684 получен через docker port после проверки
/dlab-quiz-stage1-pg и codex.task=quiz-games-stage1. Дополнительный положительный HTTP
create+идемпотентный retry прошёл отдельным прогоном 1 тест (33s) после расширения
утверждений этого существующего теста. Полные отчёты `.run-logs/quiz-stage5-pg-reports`,
дополнительный HTTP — `.run-logs/quiz-stage5-pg-http-reports`.
Перед stop имя/label/state проверены повторно; контейнер остановлен и сохранён.
Полный H2 build/i18nVerify запущен с очищенными process env и --rerun-tasks;
bootRun dlab отсутствовал, посторонние Java-процессы не затрагивались.

Финальная приёмка 2026-10-10: полный H2 build i18nVerify --rerun-tasks —
401 тест / 70 классов, failures/errors/skipped=0; 349 ключей RU/KK,
bootJar/jar и BUILD SUCCESSFUL (3m 15s). Отчёты обновлены в
.run-logs/quiz-stage5-h2-reports.
Последняя проверка PostgreSQL после защиты от expiry во время формирования batch:
25 room + 4 event = 29 PASS, failures/errors/skipped=0. Перед повторным start проверены
точное имя/label; новый фактический порт 64652 получен через docker port.
Отчёты .run-logs/quiz-stage5-pg-final-room-reports. Перед последним stop снова проверены
/dlab-quiz-stage1-pg и codex.task=quiz-games-stage1; контейнер остановлен и сохранён.
ROOM-01..14 / SEC-ROOM-01 / TEST-ROOM-01 DONE; этап 6 не начат, каталог false,
UX-03 IN_PROGRESS. Рабочая/production-БД не менялась, коммитов/отправки нет.

## Этап 6: ученический UI и клиентские проверки

К прежней PostgreSQL-маске catalog.* добавлен QuizStudentUiIntegrationTest:
12 исполнений, RANDOM_PORT, синтетические @ui.test профили и собственные банки/игры.
После каждого теста архивируются только свои материалы и отменяются свои WAITING;
история не удаляется. Флаг true задаётся только тестовым контекстом.

Клиентские проверки (Node, без браузера/БД):

```powershell
pnpm --dir scripts/quiz-ui install --frozen-lockfile --ignore-scripts
pnpm --dir scripts/quiz-ui test
```

23 проверки: нормализация кода, boolean false, все typed форматы, таймерные пороги,
личный разбор, реальный DOM клиента, CSRF, блокировка, фокус, safe text, poll,
offline retry, приглашение, settings revision и CANCELLED/EXPIRED.

Для ручного браузерного прохода опционально установить process env QUIZ_UI_SMOKE=1
и запустить только allGameTypesHaveRealServerPagesAndOptionalBrowserAcceptance
тем же Gradle wrapper/init-script, очистив QUIZ_TEST_DB_* для H2. Тест публикует
синтетический набор всех форматов и пишет .run-logs/quiz-ui-smoke-info.json (run,
фактический RANDOM_PORT, host/guest, game codes). Эти тестовые профили используют
пароль quiz-ui-test. localhost и 127.0.0.1 дают две независимые браузерные сессии.
Только этот тестовый Clock принимает marker .run-logs/quiz-ui-smoke-time вида
run:offsetSeconds. Завершение — .run-logs/quiz-ui-smoke-done с тем же run;
максимум 30 минут, затем обычный cleanup. Production-код marker не читает.
Для обычных прогонов обязательно удалить process env QUIZ_UI_SMOKE.

Браузерный проход выполнен: каталог → solo start двумя действиями, fact false,
результат/replay, host/guest join/ready/skip, reload, все четыре предметных формата,
слово с четырьмя переводами, CANCELLED/EXPIRED. RU/KK, desktop и 360×800:
нет горизонтального overflow; строки выбора 60 px, submit/skip 44 px.
Снимок .run-logs/quiz-stage6-mobile-kk.png. Ошибок/warnings консоли на word-экране нет.
Фокус/AX-структура и клавиатурные действия проверены; речевой проход настоящим
скринридером не выполнен. UI-10 и UX-03 остаются IN_PROGRESS.

Финальные H2/build/i18n и PostgreSQL выполняются по прежним командам и memory profile.
Новых миграций этапа 6 нет; V1–V53 сохранены. Каталог default false, этап 7 не начат.

PostgreSQL 2026-10-10: 167 тестов / 19 классов, failures/errors/skipped=0,
BUILD SUCCESSFUL (3m 20s). Фактический порт 54627; имя/label перед start/stop
подтверждены. Контейнер остановлен и сохранён. XML и HTML отчёты скопированы с
-Recurse и проверены по числу 167/19: .run-logs/quiz-stage6-pg-reports/test и html.

Полный H2 build i18nVerify --rerun-tasks: 413 тестов / 71 класс,
failures/errors/skipped=0, BUILD SUCCESSFUL (4m 38s). i18nVerify — 424 ключа RU/KK;
bootJar/jar собраны. Отчёты .run-logs/quiz-stage6-h2-reports/test и html,
число XML 71 и сумма 413 проверены. Node/jsdom 23 PASS. После suite менялись
только CSS целей ссылок 44×44 и комментарии; assemble i18nVerify обновляет упаковку.
UI-01..09 / TEST-UI-01 DONE; UI-10 и UX-03 IN_PROGRESS до речевого скринридера.
Этап 7 не начат; default false сохранён, рабочая БД не изменена.

Заключительный assemble i18nVerify — BUILD SUCCESSFUL (26s), 424 ключа;
bootJar/jar обновлены с последними CSS и комментариями.
