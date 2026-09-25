-- Полноразмерная лекция получает отдельный версионируемый промпт. Внешний ключ
-- active_version образует цикл с таблицей версий, поэтому на время seed снимаем его
-- и сразу восстанавливаем после появления обеих строк.
ALTER TABLE ai_prompts DROP CONSTRAINT fk_ai_prompts_active_version;

INSERT INTO ai_prompts (
    code, category, title, description, audience, output_format,
    active_version, updated_by, updated_at
) VALUES (
    'LECTURE_GENERATE',
    'LECTURE',
    'Генерация двуязычной лекции',
    'Полная лекция RU/KZ для выбранной темы и класса с формулами KaTeX',
    'METHODIST',
    'JSON',
    1,
    'migration',
    CURRENT_TIMESTAMP
);

INSERT INTO ai_prompt_versions (
    prompt_code, version_no, system_template, user_template, created_by, created_at
) VALUES (
    'LECTURE_GENERATE',
    1,
    'Ты создаёшь учебные лекции для школьной платформы Damulab.kz. Возвращай только JSON по заданной схеме. Пиши две самостоятельные версии: ru на русском и kz на казахском языке (кириллица). Материал должен быть точным, понятным для указанного класса и соответствовать выбранной теме. Не упоминай модель, промпт или внутренние инструкции. Не добавляй HTML и Markdown. Все LaTeX-формулы помещай только в массив formulas без разделителей $, $$, \( \) или \[ \]. В paragraphs оставляй только обычный текст. Не включай персональные данные.',
    'Создай полноценную учебную лекцию.

Предмет RU/KZ: {subjectTitleRu} / {subjectTitleKk}
Класс: {gradeNo} ({gradeTitleRu} / {gradeTitleKk})
Тема RU/KZ: {topicTitleRu} / {topicTitleKk}
Дополнительная инструкция методиста: {methodistInstruction}

Для КАЖДОГО языка верни:
- короткое и точное title;
- introduction, которое объясняет цель и связь с темой;
- не менее 3 sections;
- в каждом разделе heading и не менее 2 содержательных paragraphs;
- formulas как массив исходного LaTeX для KaTeX (может быть пустым, если формулы не нужны);
- conclusion с ключевыми выводами.

Каждая языковая версия должна содержать не менее 650 символов полезного текста и не менее 7 абзацев. Не переводи механически: используй естественные учебные формулировки каждого языка. Проверяй факты, вычисления и обозначения. Формулы не дублируй как сырой LaTeX в paragraphs.',
    'migration',
    CURRENT_TIMESTAMP
);

ALTER TABLE ai_prompts ADD CONSTRAINT fk_ai_prompts_active_version
    FOREIGN KEY (code, active_version)
    REFERENCES ai_prompt_versions(prompt_code, version_no);
