-- Общий каталог промптов не зависит от конкретной AI-фичи. Активная версия
-- хранится указателем, а предыдущие версии остаются доступны для аудита и отката.
CREATE TABLE ai_prompts (
    code VARCHAR(96) PRIMARY KEY,
    category VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    audience VARCHAR(32) NOT NULL,
    output_format VARCHAR(32) NOT NULL,
    active_version INTEGER NOT NULL,
    updated_by VARCHAR(320) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ai_prompts_audience
        CHECK (audience IN ('STUDENT', 'METHODIST', 'INTERNAL')),
    CONSTRAINT chk_ai_prompts_output_format
        CHECK (output_format IN ('TEXT', 'MARKDOWN', 'JSON')),
    CONSTRAINT chk_ai_prompts_active_version
        CHECK (active_version > 0)
);

CREATE TABLE ai_prompt_versions (
    prompt_code VARCHAR(96) NOT NULL,
    version_no INTEGER NOT NULL,
    system_template TEXT NOT NULL,
    user_template TEXT NOT NULL,
    created_by VARCHAR(320) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (prompt_code, version_no),
    CONSTRAINT fk_ai_prompt_versions_prompt
        FOREIGN KEY (prompt_code) REFERENCES ai_prompts(code) ON DELETE CASCADE,
    CONSTRAINT chk_ai_prompt_versions_version
        CHECK (version_no > 0)
);

INSERT INTO ai_prompts (
    code, category, title, description, audience, output_format,
    active_version, updated_by, updated_at
) VALUES
    (
        'TRANSLATION_TRANSLATE',
        'TRANSLATION',
        'Перевод текста',
        'Казахско-русский и русско-казахский перевод',
        'STUDENT',
        'TEXT',
        1,
        'migration',
        CURRENT_TIMESTAMP
    ),
    (
        'TRANSLATION_EXPLAIN',
        'TRANSLATION',
        'Объяснение перевода',
        'Учебный разбор пары исходный текст — перевод',
        'STUDENT',
        'MARKDOWN',
        1,
        'migration',
        CURRENT_TIMESTAMP
    );

INSERT INTO ai_prompt_versions (
    prompt_code, version_no, system_template, user_template, created_by, created_at
)
SELECT
    'TRANSLATION_TRANSLATE', 1, system_prompt, user_prompt_template, updated_by, updated_at
FROM ai_translation_prompts
WHERE prompt_code = 'TRANSLATE';

INSERT INTO ai_prompt_versions (
    prompt_code, version_no, system_template, user_template, created_by, created_at
)
SELECT
    'TRANSLATION_EXPLAIN', 1, system_prompt, user_prompt_template, updated_by, updated_at
FROM ai_translation_prompts
WHERE prompt_code = 'EXPLAIN';

-- Указатель не может ссылаться на отсутствующую или чужую версию.
ALTER TABLE ai_prompts ADD CONSTRAINT fk_ai_prompts_active_version
    FOREIGN KEY (code, active_version)
    REFERENCES ai_prompt_versions(prompt_code, version_no);

CREATE INDEX idx_ai_prompts_category ON ai_prompts(category);
CREATE INDEX idx_ai_prompt_versions_created_at ON ai_prompt_versions(created_at);

DROP TABLE ai_translation_prompts;
