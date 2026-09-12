package kz.damulab.ai;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Текущий маршрут AI для одного сценария. В таблице есть независимые строки
 * QUESTIONS, LECTURES и TRANSLATIONS. API-ключи и endpoint-ы здесь не хранятся —
 * они остаются серверными секретами в {@link AiProviderProperties}.
 */
@Entity
@Table(name = "ai_runtime_settings")
public class AiRuntimeSetting {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", length = 32)
    private AiUsageType usageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiProviderCode provider;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    @Column(name = "updated_by", nullable = false, length = 320)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AiRuntimeSetting() {
    }

    public AiRuntimeSetting(AiUsageType usageType, AiProviderCode provider, String modelName, String updatedBy) {
        this.usageType = usageType;
        update(provider, modelName, updatedBy);
    }

    /** Обновляет маршрут атомарно и сохраняет автора изменения для диагностики. */
    public void update(AiProviderCode provider, String modelName, String updatedBy) {
        this.provider = provider;
        this.modelName = modelName.trim();
        this.updatedBy = updatedBy;
        this.updatedAt = OffsetDateTime.now();
    }

    public AiUsageType getUsageType() {
        return usageType;
    }

    public AiProviderCode getProvider() {
        return provider;
    }

    public String getModelName() {
        return modelName;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
