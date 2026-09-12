package kz.damulab.ai;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Редактируемая пара system/user-промптов для одной операции переводчика. */
@Entity
@Table(name = "ai_translation_prompts")
public class AiTranslationPrompt {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "prompt_code", length = 32)
    private AiTranslationPromptCode promptCode;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "user_prompt_template", nullable = false, columnDefinition = "TEXT")
    private String userPromptTemplate;

    @Column(name = "updated_by", nullable = false, length = 320)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AiTranslationPrompt() {
    }

    /** Обновляет текст промпта и сохраняет автора изменения для аудита. */
    public void update(String systemPrompt, String userPromptTemplate, String updatedBy) {
        this.systemPrompt = systemPrompt.strip();
        this.userPromptTemplate = userPromptTemplate.strip();
        this.updatedBy = updatedBy;
        this.updatedAt = OffsetDateTime.now();
    }

    public AiTranslationPromptCode getPromptCode() {
        return promptCode;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getUserPromptTemplate() {
        return userPromptTemplate;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
