package kz.damulab.ai;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Метаданные логического промпта и указатель на его активную версию. */
@Entity
@Table(name = "ai_prompts")
public class AiPrompt {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 96)
    private AiPromptCode code;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiPromptAudience audience;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_format", nullable = false, length = 32)
    private AiPromptOutputFormat outputFormat;

    @Column(name = "active_version", nullable = false)
    private int activeVersion;

    @Column(name = "updated_by", nullable = false, length = 320)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AiPrompt() {
    }

    /** Переключает активную версию после сохранения неизменяемого снимка. */
    public void activate(int version, String actor) {
        this.activeVersion = version;
        this.updatedBy = actor;
        this.updatedAt = OffsetDateTime.now();
    }

    public AiPromptCode getCode() {
        return code;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public AiPromptAudience getAudience() {
        return audience;
    }

    public AiPromptOutputFormat getOutputFormat() {
        return outputFormat;
    }

    public int getActiveVersion() {
        return activeVersion;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
