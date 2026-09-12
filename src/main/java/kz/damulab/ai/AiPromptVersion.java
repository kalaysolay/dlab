package kz.damulab.ai;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Неизменяемый снимок system/user-шаблонов одного логического промпта. */
@Entity
@Table(name = "ai_prompt_versions")
@IdClass(AiPromptVersionId.class)
public class AiPromptVersion {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "prompt_code", length = 96)
    private AiPromptCode promptCode;

    @Id
    @Column(name = "version_no")
    private int versionNo;

    @Column(name = "system_template", nullable = false, columnDefinition = "TEXT")
    private String systemTemplate;

    @Column(name = "user_template", nullable = false, columnDefinition = "TEXT")
    private String userTemplate;

    @Column(name = "created_by", nullable = false, length = 320)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AiPromptVersion() {
    }

    public AiPromptVersion(
            AiPromptCode promptCode,
            int versionNo,
            String systemTemplate,
            String userTemplate,
            String createdBy
    ) {
        this.promptCode = promptCode;
        this.versionNo = versionNo;
        this.systemTemplate = systemTemplate.strip();
        this.userTemplate = userTemplate.strip();
        this.createdBy = createdBy;
        this.createdAt = OffsetDateTime.now();
    }

    public AiPromptCode getPromptCode() {
        return promptCode;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getSystemTemplate() {
        return systemTemplate;
    }

    public String getUserTemplate() {
        return userTemplate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
