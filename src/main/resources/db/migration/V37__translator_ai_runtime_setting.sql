-- Переводчик получает собственный маршрут: администратор может менять модель,
-- не затрагивая генерацию вопросов и мини-лекций.
ALTER TABLE ai_runtime_settings DROP CONSTRAINT chk_ai_runtime_settings_usage;
ALTER TABLE ai_runtime_settings ADD CONSTRAINT chk_ai_runtime_settings_usage
    CHECK (usage_type IN ('QUESTIONS', 'LECTURES', 'TRANSLATIONS'));

-- По умолчанию внешний трафик запрещён до явного выбора провайдера администратором.
INSERT INTO ai_runtime_settings (usage_type, provider, model_name, updated_by)
VALUES ('TRANSLATIONS', 'STUB', 'stub', 'migration');
