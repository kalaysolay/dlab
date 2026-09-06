# Формат импорта лекций Damulab

Версия: `1.0`.

Это системная вводная для агента, который пишет лекции. Агент возвращает только валидный JSON без Markdown-обёртки и пояснений вне JSON. Полный пример: [`lesson-import-example.json`](lesson-import-example.json).

## Принцип формата

Тело передаётся как безопасный Quill-совместимый HTML:

- RU и KK находятся в отдельных `ruHtml` и `kkHtml`;
- формулы остаются исходным LaTeX в `span.ql-formula`;
- изображения объявляются в `assets`, а в HTML вставляются через `asset://id`;
- импортёр загружает изображения, заменяет placeholders и применяет общий sanitizer лекций.

Нельзя передавать Markdown, сгенерированный KaTeX HTML, внешний URL или `data:` URL непосредственно в `<img>`.

## API

```text
POST /api/admin/lectures/import
Content-Type: application/json
ROLE_ADMIN + CSRF
```

Успех — HTTP `201`:

```json
{
  "importedCount": 1,
  "lessons": [
    {
      "externalId": "math-4-finding-percent-001",
      "lectureId": 42,
      "status": "draft"
    }
  ]
}
```

Импорт всегда создаёт `draft`. Публикация остаётся отдельным действием методиста. Batch импортируется атомарно: ошибка одной лекции откатывает весь batch и удаляет уже загруженные изображения.

## Корень и лекция

```json
{
  "schemaVersion": "1.0",
  "kind": "damulab.lesson-import",
  "lessons": [
    {
      "externalId": "math-4-finding-percent-001",
      "metadata": {},
      "assets": [],
      "content": {
        "ruHtml": "<p>...</p>",
        "kkHtml": "<p>...</p>"
      },
      "attachments": [],
      "completionControl": {
        "mode": "AUTO",
        "questionCount": 3
      }
    }
  ]
}
```

- `schemaVersion` и `kind` имеют точные значения выше.
- `lessons` содержит от 1 до 100 лекций.
- `externalId` стабилен между запусками агента, содержит строчные латинские буквы, цифры и дефисы, максимум 128 символов. Повторный ID возвращает `409 Conflict`.

## Метаданные

```json
{
  "metadata": {
    "subject": {
      "code": "math",
      "title": { "ru": "Математика", "kk": "Математика" }
    },
    "grade": { "number": 4 },
    "topic": {
      "path": ["percent-basics", "finding-percent-of-number"],
      "title": {
        "ru": "Нахождение процента от числа",
        "kk": "Санның пайызын табу"
      }
    },
    "title": {
      "ru": "Как найти процент от числа",
      "kk": "Санның пайызын қалай табуға болады"
    },
    "primaryLanguage": "kk",
    "source": "Авторский материал, подготовленный для Damulab"
  }
}
```

- Предмет разрешается по `subject.code`, класс — по `grade.number`.
- Тема разрешается по полному `topic.path` от корня до целевой темы. Поиск только по названию или последнему коду запрещён.
- RU/KK названия предмета и целевой темы сверяются со справочником.
- Предмет, класс и тема должны существовать; импорт не меняет учебный граф.
- `title.ru`, `title.kk` и `source` обязательны. `source` — максимум 512 символов, без выдуманных источников.
- `primaryLanguage` — `kk` или `ru`; для нового контента по умолчанию использовать `kk`.

## HTML

```html
<h2>Что означает процент</h2>
<p><strong>25%</strong> — это 25 частей из 100.</p>
<p><span class="ql-formula"
         data-value="p=\frac{a}{b}\cdot 100\%"
         contenteditable="false"></span></p>
<img src="asset://percent-grid" alt="Из ста клеток закрашены двадцать пять">
```

Поддерживаются элементы текущего редактора:

- `p`, `br`, `h1`–`h6` (рекомендуются `h2`, `h3`);
- `strong`, `b`, `em`, `i`, `u`, `s`, `sub`, `sup`;
- `ul`, `ol`, `li`, `blockquote`, `pre`, `code`, `hr`;
- `a` с безопасным `http`, `https`, `mailto` или относительным URL;
- `table`, `thead`, `tbody`, `tr`, `th`, `td`;
- `span.ql-formula` и `img src="asset://..."`.

`script`, `style`, `iframe`, event-атрибуты, произвольный CSS и неподдерживаемые теги удаляются. Внешняя ссылка с `target="_blank"` получает `rel="noopener noreferrer nofollow"`.

### Формулы

Формула задаётся только так:

```html
<span class="ql-formula" data-value="S=\pi r^2" contenteditable="false"></span>
```

- `data-value` содержит KaTeX-совместимый LaTeX без `$...$`, `$$...$$`, `\(...\)` и `\[...\]`.
- В HTML используется один обратный слеш. В JSON он экранируется: HTML `\frac` записывается как `\\frac`.
- Максимальная длина после декодирования JSON — 1000 символов.
- Запрещены `\htmlClass`, `\htmlId`, `\htmlStyle`, `\htmlData`, `\href`, `\url`, `\includegraphics`.
- Если sanitizer изменил или удалил формулу, весь импорт отклоняется — формула не теряется молча.
- Существенную формулу нужно объяснить соседним текстом и определить переменные.

### Изображения в тексте

```html
<img src="asset://percent-grid"
     alt="Из ста клеток закрашены двадцать пять"
     title="Иллюстрация 25 процентов">
```

- `src` обязательно имеет вид `asset://id`; любые иные URL отклоняются.
- `alt` обязателен и локализуется отдельно в RU и KK.
- Один asset без надписей можно использовать в обоих языках.
- Для изображения с текстом нужны отдельные RU и KK assets.

## Assets

HTTP API версии `1.0` принимает только Base64:

```json
{
  "id": "percent-grid",
  "kind": "image",
  "mimeType": "image/png",
  "source": {
    "type": "base64",
    "data": "iVBORw0KGgo..."
  },
  "sha256": "необязательный SHA-256 в нижнем регистре",
  "credit": "Автор и лицензия либо указание, что схема авторская"
}
```

- Поддерживаются PNG, JPEG, GIF, WebP; SVG запрещён.
- Максимальный декодированный размер — 10 МБ.
- Base64 передаётся без `data:image/...;base64,`.
- Сервер проверяет сигнатуру, `mimeType` и необязательный `sha256`.
- Asset ID уникален и соответствует `[a-z0-9][a-z0-9-]{0,63}`.
- Неизвестный и неиспользуемый asset являются ошибками.
- URL-загрузка исключена из v1 из-за SSRF, серверные `file`-пути — из-за доступа к файловой системе.
- Агент не выдумывает Base64, хеш или лицензию: asset включается только после получения реальных байтов изображения.

## Вложения

До 8 ссылок на дополнительные материалы:

```json
{
  "title": "Жаттығулар / Упражнения",
  "url": "https://example.org/exercises.pdf",
  "mediaType": "pdf"
}
```

`mediaType`: `link`, `pdf`, `video`, `image`. Для специализированного типа расширение/URL должно ему соответствовать. Бинарные вложения через JSON v1 не загружаются.

## Контроль прохождения

```json
{
  "mode": "AUTO",
  "questionCount": 3
}
```

В v1 допускается только `AUTO`; `questionCount` — от 1 до 10, рекомендуется `3`. Система выбирает опубликованные вопросы той же темы. `MANUAL` и ID вопросов пока запрещены.

## Казахский и русский языки

- Обе локализации обязательны.
- Казахский текст пишется на современном литературном казахском языке кириллицей с корректными `ә`, `ғ`, `қ`, `ң`, `ө`, `ұ`, `ү`, `һ`, `і`.
- RU и KK содержат одинаковые факты, числа, формулы, примеры и смысл изображений.
- Переменные формул не переводятся.
- Структуру заголовков, списков, формул и изображений следует сохранять параллельной.

## Чек-лист агента

1. Вывести только JSON без code fence.
2. Проверить `schemaVersion`, `kind` и уникальный `externalId`.
3. Указать существующие предмет, класс и полный путь темы.
4. Заполнить RU и KK заголовки и HTML.
5. Не использовать Markdown или generated KaTeX HTML.
6. Представить формулы как `span.ql-formula` и экранировать LaTeX для JSON.
7. Для каждого `<img>` использовать существующий `asset://id` и локализованный `alt`.
8. Не включать фиктивные или неиспользуемые assets.
9. Всегда установить `completionControl.mode` в `AUTO`.

## Коды ошибок

- envelope: `lecture_import_schema_version_invalid`, `lecture_import_kind_invalid`;
- идентификатор: `lecture_import_external_id_duplicate` (`409`);
- граф: `lecture_import_subject_not_found`, `lecture_import_grade_not_found`, `lecture_import_topic_not_found`, `lecture_import_subject_title_mismatch`, `lecture_import_topic_title_mismatch`;
- assets: `lecture_import_asset_duplicate`, `lecture_import_asset_source_invalid`, `lecture_import_asset_base64_invalid`, `lecture_import_asset_mime_mismatch`, `lecture_import_asset_sha256_mismatch`, `lecture_import_asset_not_found`, `lecture_import_unused_asset`;
- HTML: `lecture_import_image_source_invalid`, `lecture_import_image_alt_required`, `lecture_import_formula_invalid`, `lecture_import_content_empty`;
- контроль: `lecture_import_control_mode_invalid`;
- поля и диапазоны: `validation_failed`.
