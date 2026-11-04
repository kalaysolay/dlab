# Выкладка 4 ноября 2026

## Текущий релиз: 40d625f

По запросу владельца снят лимит трёх новых разговоров в день. Счётчик стартов
сохраняется для статистики; старые значения не требуют сброса. Квоты аудио,
обращений к моделям, денежные резервы и одна активная сессия сохранены.
Образ `damulab-speaking:40d625f`, каталог `/home/deploy/releases/speaking-40d625f`.
Перед обновлением сохранены database-before.dump и образ
`damulab-speaking:previous-40d625f` (4df9c19). Миграций нет, PostgreSQL не пересоздавался.

```bash
cd /home/deploy/releases/speaking-40d625f
docker compose --project-directory /opt/damulab --env-file .env.prod \
  -f compose-before.yml -f override.json up -d --no-deps app
```

Полная сборка и i18nVerify прошли: 526 тестов, 551 ключ ru/kk. Регрессия проверяет
создание четвёртого разговора при сохранённом дневном счётчике. На локальном
стенде с прежними тремя разговорами новый старт вернул 201. После деплоя проверены
login=200, публичный JS и отсутствие speaking_daily_limit в классе сервиса из
фактически запущенного JAR. Код закоммичен и отправлен в feat/english-speaking.

## Предыдущий релиз: 4df9c19

Опубликована новая мобильная компоновка разговора и устойчивый оформленный
переход /app. Основное изменение — e5c5616, финальные safe-area/overscroll стили —
4df9c19. [Поведение и результаты проверки](MOBILE_LAYOUT_UPDATE.md).
Активный каталог `/home/deploy/releases/speaking-4df9c19`, образ
`damulab-speaking:4df9c19`. Сохранены database-before.dump и предыдущий образ
`damulab-speaking:previous-4df9c19` (e5c5616). Схема V71 не менялась.

```bash
cd /home/deploy/releases/speaking-4df9c19
docker compose --project-directory /opt/damulab --env-file .env.prod \
  -f compose-before.yml -f override.json up -d --no-deps app
```

Проверены login=200, публичные pwa-resume.js?v=1, мобильная speaking.css,
manifest focus-existing и SW v7. Обновлено только приложение; production PostgreSQL
продолжает работать. Установленную PWA нужно один раз перезапустить для загрузки
нового документа/JS. Проверка на реальном телефоне остаётся за владельцем.

## Предыдущий релиз: 078005b

Опубликовано общее исправление восстановления PWA: возврат из фона сохраняет
документ, запуск через иконку фокусирует существующее окно, прежний start_url
восстанавливает допустимый маршрут текущей вкладки. [Описание и проверки](../../docs/PASSKEYS.md).
Образ — `damulab-speaking:078005b`, каталог — `/home/deploy/releases/speaking-078005b`.
Сохранены свежий database-before.dump и образ `damulab-speaking:previous-078005b`
(код c324e1b). Схема БД не менялась, обновлено только app. После запуска проверены
login=200, новый публичный app-entry.js, manifest focus-existing и SW cache v6.

```bash
cd /home/deploy/releases/speaking-078005b
docker compose --project-directory /opt/damulab --env-file .env.prod \
  -f compose-before.yml -f override.json up -d --no-deps app
```

Коммит запушен в feat/english-speaking. Полная сборка: 526 тестов без падений;
два Node-набора проверяют resume и приватный SW-cache. Старое открытое PWA нужно
однократно перезапустить для загрузки нового JS; физическая мобильная проверка
не заменяется этими тестами. Предыдущие релизы ниже сохранены как история.

## Предыдущий релиз: c324e1b

После обратной связи с телефона опубликован `c324e1b`: автоматическая озвучка всей новой реплики и отсутствие автофокуса расшифровки. [Поведение и проверки](MOBILE_VOICE_UPDATE.md). Активный каталог — `/home/deploy/releases/speaking-c324e1b`, образ — `damulab-speaking:c324e1b`. В нём сохранены fresh `database-before.dump`, конфигурация и скрипт обновления; предыдущий образ — `damulab-speaking:previous-c324e1b` (код 7c3d29d). Схема осталась V71; обновлено только приложение, PostgreSQL не перезапускался. Публичный login=200, приватный speaking без входа=302, новый JavaScript подтверждён по публичному URL.

Команды для релиза c324e1b (история):

```bash
cd /home/deploy/releases/speaking-c324e1b
docker compose --project-directory /opt/damulab --env-file .env.prod \
  -f compose-before.yml -f override.json up -d --no-deps app
docker logs --tail 100 damulab-app-1
```

При неуспешной проверке `/login` скрипт этого обновления возвращает предыдущий образ. В фактическом запуске откат не потребовался. Физическое поведение autoplay/клавиатуры после обновления должен проверить владелец на телефоне.

## Первая выкладка: 7c3d29d

Опубликован код `7c3d29d` ветки `feat/english-speaking` на `https://damulab.kz`. Пользователь разрешил открыть разговорник всем ученикам: в системе его семейные аккаунты. Методистская приёмка и физические мобильные устройства остаются открытыми; публикация не означает их прохождение.

## Фактическая схема

- SSH: `deploy@148.135.208.50`, существующий пользовательский ключ `id_ed25519_vps`.
- Compose project `damulab`, приложение `damulab-app-1`, PostgreSQL `damulab-postgres-1`, nginx обращается к loopback 8080.
- Исходный `/opt/damulab` оставлен с его локальными правками compose и скриптами `english-v3`. Его Git HEAD — `40bb69d`; каталог не переключался на feature-ветку.
- Первый релиз: `/home/deploy/releases/speaking-7c3d29d`. Исходники экспортированы из точного коммита в `source`, образ `damulab-speaking:7c3d29d` собран на VPS. Позднее заменён релизом c324e1b выше.
- `compose-before.yml` сохраняет действующую конфигурацию сервера, `override.json` задаёт образ/контекст и speaking-флаги. `.env.prod` и `voice.env` имеют режим 0600 и не входят в Git/образ.

Реальный диалог: DeepSeek `deepseek-flash`, отдельная строка SPEAKING. Остальные AI-маршруты сохранены. STT — Deepgram Nova-3, озвучка — OpenAI gpt-4o-mini-tts. Флаги module/cloud/voice/speech=true, pilot-only=false по указанию пользователя. Начальные бюджетные резервы 3 USD/ученик/день, 20 USD/модуль/день; это не инвойс.

## Выполненные проверки

Старый JAR сохранён; нормализованные SQL-миграции до V63 совпали с веткой. Перед заменой создан архив `database-before.dump`, проверен pg_restore --list и выполнено восстановление в отдельную PostgreSQL. Новый образ успешно применил V67–V71 на этой копии и запустился с выключенными внешними AI, без ключей и исходящей сети. Временные контейнеры, их том БД и сеть после проверки удалены.

Сохранены старый образ `damulab-speaking:previous-7c3d29d`, JAR, compose и архивы трёх файловых volumes. Приложение обновлено через `up -d --no-deps app`; production PostgreSQL не пересоздавался. Сначала применены миграции с закрытым speaking, затем настроен SPEAKING и включены внешние функции. Количества пользователей и quiz_games до/после совпали.

Публичный `/login` отвечает 200; приватный speaking без входа переводит на login. Серверные ключи DeepSeek и OpenAI прошли синтетический текстовый запрос; Deepgram распознал синтетический MP3, OpenAI TTS вернул 48 768 байт аудио. В runtime доступен ffmpeg. Проверка настоящего голоса/полного диалога под семейным production-аккаунтом ещё не выполнялась: пароли аккаунтов не использовались.

## Управление первым релизом (история)

На VPS:

```bash
cd /home/deploy/releases/speaking-7c3d29d
docker compose --project-directory /opt/damulab --env-file .env.prod \
  -f compose-before.yml -f override.json up -d --no-deps app
docker logs --tail 100 damulab-app-1
curl -I https://damulab.kz/login
```

Обычный `git pull` и build из `/opt/damulab` пока соберут другой код. Для следующего релиза нужно подготовить новый каталог с проверенным коммитом, собрать отдельный тег, сохранить резервные копии и явно выбрать новый override. Существующий release-каталог и его тег не перезаписывать новой версией.

Для аварийного закрытия раздела установить `SPEAKING_ENABLED=false` в environment файла override.json и выполнить приведённый `up`. Для отключения внешних функций также выключить cloud/voice/speech. Простой `docker compose restart` не применяет изменения environment или новый образ.

Старый образ и резервные копии сохранены для отката. Переключение образа на предыдущий требует проверки совместимости старого приложения с V71; такой rollback отдельно не репетировался. Не удалять новые таблицы и не использовать Flyway repair/clean. Восстановление всего дампа требует отдельного решения из-за потери новых данных и возврата ранее удалённых цитат. Правила хранения/удаления production-копий ещё должны быть определены владельцем.
