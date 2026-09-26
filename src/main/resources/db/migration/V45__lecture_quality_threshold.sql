-- Порог проверки полноразмерной лекции редактируется вместе с runtime-маршрутом AI.
-- Значение хранится для каждой строки, но применяется только к usage_type=LECTURES.
ALTER TABLE ai_runtime_settings
    ADD COLUMN quality_threshold INTEGER NOT NULL DEFAULT 90;

ALTER TABLE ai_runtime_settings
    ADD CONSTRAINT chk_ai_runtime_settings_quality_threshold
        CHECK (quality_threshold BETWEEN 0 AND 100);
