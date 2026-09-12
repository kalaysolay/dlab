-- Переводу и короткому учебному разбору важнее низкая задержка и стоимость.
-- Маршруты QUESTIONS и LECTURES намеренно не меняем.
UPDATE ai_runtime_settings
SET provider = 'DEEPSEEK',
    model_name = 'deepseek-v4-flash',
    updated_by = 'migration',
    updated_at = CURRENT_TIMESTAMP
WHERE usage_type = 'TRANSLATIONS';
