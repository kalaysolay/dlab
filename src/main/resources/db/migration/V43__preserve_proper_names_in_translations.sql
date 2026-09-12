-- Имена собственные сохраняют личность носителя: модель транслитерирует написание,
-- но не подменяет имя похожим именем из языка перевода.
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
Preserve personal names as proper names. Transliterate only the spelling when the target alphabet requires it; never translate the meaning of a name or replace it with a culturally similar, localized, biblical, or otherwise related name. For Kazakh-to-Russian translation, write Жақып as Жакып, not Якуп or Яков. Likewise, Jonathan may be transliterated as Джонатан but must never become Жанибек. Apply the same identity-preserving rule in both translation directions.',
    version.user_template,
    'migration',
    CURRENT_TIMESTAMP
FROM ai_prompts prompt
JOIN ai_prompt_versions version
  ON version.prompt_code = prompt.code
 AND version.version_no = prompt.active_version
WHERE prompt.code = 'TRANSLATION_TRANSLATE';

UPDATE ai_prompts
SET active_version = active_version + 1,
    updated_by = 'migration',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'TRANSLATION_TRANSLATE';
