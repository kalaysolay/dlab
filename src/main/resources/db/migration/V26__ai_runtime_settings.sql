CREATE TABLE ai_runtime_settings (
    usage_type VARCHAR(32) PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    updated_by VARCHAR(320) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ai_runtime_settings_usage
        CHECK (usage_type IN ('QUESTIONS', 'LECTURES')),
    CONSTRAINT chk_ai_runtime_settings_provider
        CHECK (provider IN ('STUB', 'OPENAI', 'DEEPSEEK'))
);

-- Безопасный старт после миграции: внешний API не вызывается, пока администратор
-- явно не выберет провайдера и модель для каждого сценария в новой настройке.
INSERT INTO ai_runtime_settings (usage_type, provider, model_name, updated_by)
VALUES
    ('QUESTIONS', 'STUB', 'stub', 'migration'),
    ('LECTURES', 'STUB', 'stub', 'migration');
