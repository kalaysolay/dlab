# Банк слов и одиночная викторина: ранний проект данных

> **Устаревший черновик.** После продуктового решения «комната существует всегда» отдельные `solo_quiz_sessions` больше не являются целевой моделью. Актуальные требования и целевая схема описаны в [SPECIFICATION.md](SPECIFICATION.md). Этот файл сохранён только как история раннего исследования банка слов.

## Цель

Добавить отдельный, курируемый банк слов для одиночных языковых викторин. Слова нельзя выбирать из общего словаря или генерировать на лету: ученик получает только опубликованный набор, явно разрешённый для его класса.

Текущие `questions` / `question_versions` остаются банком предметных вопросов, а `quiz_rooms` — сетевой игрой. Для словарной тренировки лучше не создавать искусственные `SCQ`-вопросы на каждое слово и не превращать одиночную игру в комнату с одним участником: это смешает разные правила публикации, версионирования и аналитики.

## Основные решения

1. **Набор — граница безопасности.** У набора есть языковая пара, уровень и список разрешённых классов. В выборку попадают только наборы и слова со статусом `PUBLISHED`.
2. **Одно слово — одно значение.** Если слово многозначное, каждое школьное значение хранится отдельной записью с пояснением. Так варианты ответа не становятся двусмысленными.
3. **Переводы нормализованы.** Основной и допустимые альтернативные переводы хранятся отдельными строками, а не в JSON.
4. **Дистракторы берутся только из того же набора.** Они должны быть опубликованы, подходить тому же классу и не пересекаться с допустимыми переводами текущего слова.
5. **Раунд хранит снимок вариантов.** Изменение банка после игры не должно менять исторический результат ученика.
6. **Прогресс по слову отделён от результата сессии.** Это позволит позже добавить повторение ошибок и интервальное запоминание без переделки банка.

## Предлагаемые таблицы

### `vocabulary_sets`

Курируемый учебный набор, например `english-grade-5-basics` или `kazakh-grade-4-everyday`.

| Поле | Тип | Назначение |
| --- | --- | --- |
| `id` | `bigint` | PK |
| `code` | `varchar(128)` | стабильный уникальный код |
| `source_language` | `varchar(8)` | язык вопроса: `en`, `kk`, позже `ru` |
| `target_language` | `varchar(8)` | язык вариантов ответа, сейчас `ru` |
| `title_ru`, `title_kk` | `varchar(255)` | название набора в интерфейсе |
| `proficiency_level` | `varchar(32)` | необязательный уровень, например `A1` |
| `status` | `varchar(32)` | `DRAFT`, `IN_REVIEW`, `PUBLISHED`, `ARCHIVED` |
| `source_reference` | `varchar(512)` | учебник, программа или другой проверяемый источник |
| `created_by`, `reviewed_by` | FK → `app_users` | автор и проверивший редактор |
| `created_at`, `updated_at`, `published_at` | `timestamptz` | аудит жизненного цикла |

### `vocabulary_set_grades`

Явно связывает набор с допустимыми классами. Точная связь надёжнее диапазона `min_grade/max_grade`: набор можно разрешить, например, только для 3 и 5 классов.

| Поле | Тип | Назначение |
| --- | --- | --- |
| `set_id` | FK → `vocabulary_sets` | часть составного PK |
| `grade_id` | FK → `grades` | часть составного PK |

### `vocabulary_items`

Одно проверяемое значение исходного слова внутри набора.

| Поле | Тип | Назначение |
| --- | --- | --- |
| `id` | `bigint` | PK |
| `set_id` | FK → `vocabulary_sets` | набор и его возрастная граница |
| `source_text` | `varchar(255)` | слово, которое увидит ученик |
| `source_normalized` | `varchar(255)` | нормализованное значение для поиска дублей |
| `pronunciation` | `varchar(255)` | транскрипция или подсказка произношения |
| `audio_path` | `varchar(512)` | необязательная проверенная запись |
| `part_of_speech` | `varchar(32)` | помогает подбирать правдоподобные варианты |
| `meaning_note_ru`, `meaning_note_kk` | `varchar(512)` | уточнение значения для редактора и разбора |
| `difficulty` | `smallint` | 1–5 внутри набора |
| `status` | `varchar(32)` | собственный статус публикации слова |
| `source_reference` | `varchar(512)` | конкретная страница/раздел источника при необходимости |
| `created_by`, `reviewed_by` | FK → `app_users` | аудит редакции |
| `created_at`, `updated_at`, `published_at` | `timestamptz` | аудит жизненного цикла |

Ограничение: `unique(set_id, source_normalized, meaning_note_ru)` не даёт случайно создать дубль одного значения, но разрешает отдельные значения многозначного слова.

### `vocabulary_answers`

Проверенные переводы одного значения.

| Поле | Тип | Назначение |
| --- | --- | --- |
| `id` | `bigint` | PK |
| `item_id` | FK → `vocabulary_items` | исходное слово |
| `answer_text` | `varchar(255)` | отображаемый перевод |
| `answer_normalized` | `varchar(255)` | сравнение и защита от дублей |
| `is_primary` | `boolean` | основной вариант для карточки и разбора |

Для каждого `item_id` должен быть ровно один основной перевод. Это лучше проверять сервисом публикации; частичный уникальный индекс запрещает более одного `is_primary = true`.

### `vocabulary_distractor_exclusions`

Редкое редакторское исключение для пар, которые нельзя показывать вместе: синонимы, слишком близкие значения, региональные варианты.

| Поле | Тип | Назначение |
| --- | --- | --- |
| `item_id` | FK → `vocabulary_items` | вопрос |
| `excluded_item_id` | FK → `vocabulary_items` | слово, чей основной перевод нельзя использовать как дистрактор |
| `reason` | `varchar(255)` | причина для редактора |

## Сессия одиночной игры

### `solo_quiz_sessions`

| Поле | Тип | Назначение |
| --- | --- | --- |
| `id` | `bigint` | PK |
| `student_profile_id` | FK → `student_profiles` | ученик |
| `vocabulary_set_id` | FK → `vocabulary_sets` | опубликованный набор |
| `question_count` | `integer` | выбранное число слов |
| `round_seconds` | `integer` | лимит времени |
| `status` | `varchar(32)` | `ACTIVE`, `FINISHED`, `ABANDONED` |
| `correct_answers` | `integer` | итоговый счёт |
| `started_at`, `finished_at` | `timestamptz` | длительность сессии |

### `solo_quiz_rounds`

| Поле | Тип | Назначение |
| --- | --- | --- |
| `id` | `bigint` | PK |
| `session_id` | FK → `solo_quiz_sessions` | сессия |
| `vocabulary_item_id` | FK → `vocabulary_items` | исходное слово |
| `order_no` | `integer` | порядок показа |
| `source_text_snapshot` | `varchar(255)` | неизменяемый текст вопроса |
| `pronunciation_snapshot` | `varchar(255)` | снимок транскрипции |
| `shown_at` | `timestamptz` | начало раунда |

Ограничения: `unique(session_id, order_no)` и `unique(session_id, vocabulary_item_id)`.

### `solo_quiz_round_options`

| Поле | Тип | Назначение |
| --- | --- | --- |
| `id` | `bigint` | PK |
| `round_id` | FK → `solo_quiz_rounds` | раунд |
| `position` | `smallint` | A–D, уникально внутри раунда |
| `answer_text_snapshot` | `varchar(255)` | неизменяемый показанный текст |
| `source_answer_id` | FK → `vocabulary_answers`, nullable | происхождение варианта для аудита |
| `is_correct` | `boolean` | правильность в момент игры |

### `solo_quiz_answers`

| Поле | Тип | Назначение |
| --- | --- | --- |
| `id` | `bigint` | PK |
| `round_id` | FK → `solo_quiz_rounds`, unique | один ответ на раунд |
| `selected_option_id` | FK → `solo_quiz_round_options`, nullable | `null` для пропуска/тайм-аута |
| `is_correct` | `boolean` | результат |
| `timed_out` | `boolean` | время закончилось |
| `response_ms` | `integer` | скорость ответа |
| `answered_at` | `timestamptz` | время фиксации |

### `student_vocabulary_progress`

Агрегат для повторения ошибок и будущего интервального обучения.

| Поле | Тип | Назначение |
| --- | --- | --- |
| `student_profile_id` | FK → `student_profiles` | часть составного PK |
| `vocabulary_item_id` | FK → `vocabulary_items` | часть составного PK |
| `seen_count`, `correct_count` | `integer` | накопленная статистика |
| `correct_streak` | `integer` | серия правильных ответов |
| `mastery_level` | `smallint` | 0–5, вычисляется сервисом |
| `last_answered_at` | `timestamptz` | последнее повторение |
| `next_review_at` | `timestamptz` | следующее рекомендуемое повторение |

## Правила безопасной выборки

Сервис начала тренировки должен последовательно проверить:

1. У профиля ученика указан `grade_no`.
2. Набор имеет `status = PUBLISHED` и связан с этим классом через `vocabulary_set_grades`.
3. У слова `status = PUBLISHED`, есть ровно один основной перевод и минимум три допустимых дистрактора.
4. Все варианты относятся к тому же опубликованному набору и языковой паре.
5. Дистракторы по возможности совпадают по части речи и близки по сложности.
6. Перевод дистрактора не совпадает ни с одним допустимым переводом вопроса и не входит в `vocabulary_distractor_exclusions`.
7. Никакой генерации слов или переводов моделью во время ученической игры нет.

Если подходящих слов меньше запрошенного числа, интерфейс показывает фактически доступное количество, а не расширяет выборку за пределы класса.

## Публикационный минимум

Набор разрешается опубликовать, только когда:

- указан проверяемый источник;
- назначен хотя бы один класс;
- каждое слово прошло редакторскую проверку;
- у каждого слова один основной перевод;
- после фильтра исключений для каждого слова остаются минимум три однозначных дистрактора;
- автор и проверяющий — разные пользователи, если это требуется редакционной политикой.

## Черновой DDL ключевых таблиц

```sql
create table vocabulary_sets (
    id bigint generated by default as identity primary key,
    code varchar(128) not null unique,
    source_language varchar(8) not null,
    target_language varchar(8) not null,
    title_ru varchar(255) not null,
    title_kk varchar(255) not null,
    proficiency_level varchar(32),
    status varchar(32) not null,
    source_reference varchar(512) not null,
    created_by bigint references app_users(id),
    reviewed_by bigint references app_users(id),
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    published_at timestamp with time zone,
    constraint chk_vocabulary_set_languages check (source_language in ('en', 'kk', 'ru') and target_language in ('en', 'kk', 'ru')),
    constraint chk_vocabulary_set_direction check (source_language <> target_language)
);

create table vocabulary_set_grades (
    set_id bigint not null references vocabulary_sets(id) on delete cascade,
    grade_id bigint not null references grades(id) on delete restrict,
    primary key (set_id, grade_id)
);

create table vocabulary_items (
    id bigint generated by default as identity primary key,
    set_id bigint not null references vocabulary_sets(id) on delete cascade,
    source_text varchar(255) not null,
    source_normalized varchar(255) not null,
    pronunciation varchar(255),
    audio_path varchar(512),
    part_of_speech varchar(32),
    meaning_note_ru varchar(512) not null,
    meaning_note_kk varchar(512),
    difficulty smallint not null,
    status varchar(32) not null,
    source_reference varchar(512),
    created_by bigint references app_users(id),
    reviewed_by bigint references app_users(id),
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    published_at timestamp with time zone,
    constraint chk_vocabulary_item_difficulty check (difficulty between 1 and 5),
    unique (set_id, source_normalized, meaning_note_ru)
);

create table vocabulary_answers (
    id bigint generated by default as identity primary key,
    item_id bigint not null references vocabulary_items(id) on delete cascade,
    answer_text varchar(255) not null,
    answer_normalized varchar(255) not null,
    is_primary boolean not null default false,
    unique (item_id, answer_normalized)
);

create unique index uq_vocabulary_primary_answer
    on vocabulary_answers(item_id)
    where is_primary = true;
```

Полный DDL с игровыми таблицами лучше оформить отдельной Flyway-миграцией после согласования UX и правил редакторской публикации.
