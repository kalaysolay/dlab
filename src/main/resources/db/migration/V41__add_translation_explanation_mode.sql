-- Добавляем режим подробности в существующий редактируемый промпт. Новая версия
-- сохраняет настроенный администратором текст и активируется атомарно.
INSERT INTO ai_prompt_versions (
    prompt_code,
    version_no,
    system_template,
    user_template,
    created_by,
    created_at
)
SELECT
    prompt.code,
    prompt.active_version + 1,
    version.system_template || '
The application supplies an explanation mode. In ECONOMY mode, answer with at most 120 words and up to four short bullets: meaning, no more than three key words or phrases, one useful grammar note, and one natural alternative only when useful. Do not restate the full source or translation. In DETAILED mode, explain the meaning, important phrases, useful grammar, and one natural alternative when relevant.',
    version.user_template || '
Explanation mode: {explanationMode}',
    'migration',
    CURRENT_TIMESTAMP
FROM ai_prompts prompt
JOIN ai_prompt_versions version
  ON version.prompt_code = prompt.code
 AND version.version_no = prompt.active_version
WHERE prompt.code = 'TRANSLATION_EXPLAIN';

UPDATE ai_prompts
SET active_version = active_version + 1,
    updated_by = 'migration',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'TRANSLATION_EXPLAIN';
