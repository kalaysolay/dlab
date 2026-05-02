package kz.damulab.ai;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import kz.damulab.content.AtomicSkill;
import kz.damulab.content.Topic;
import kz.damulab.questions.QuestionType;
import kz.damulab.users.AppUser;

@Entity
@Table(name = "ai_generation_jobs")
public class AiGenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 64)
    private AiGenerationJobType jobType = AiGenerationJobType.QUESTION_GENERATION;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiGenerationJobStatus status = AiGenerationJobStatus.PENDING;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atomic_skill_id")
    private AtomicSkill atomicSkill;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 32)
    private QuestionType questionType;

    @Column(name = "requested_count", nullable = false)
    private int requestedCount;

    @Column(nullable = false)
    private int difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "language_mode", nullable = false, length = 16)
    private AiLanguageMode languageMode;

    @Column(columnDefinition = "text")
    private String instruction;

    @Column(name = "request_payload_json", nullable = false, columnDefinition = "text")
    private String requestPayloadJson;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_code", length = 128)
    private String errorCode;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    protected AiGenerationJob() {
    }

    public AiGenerationJob(
            Topic topic,
            AtomicSkill atomicSkill,
            QuestionType questionType,
            int requestedCount,
            int difficulty,
            AiLanguageMode languageMode,
            String instruction,
            String requestPayloadJson,
            AppUser createdBy
    ) {
        this.providerName = "stub";
        this.modelName = "stub-ai-content-factory-v1";
        this.topic = topic;
        this.atomicSkill = atomicSkill;
        this.questionType = questionType;
        this.requestedCount = requestedCount;
        this.difficulty = difficulty;
        this.languageMode = languageMode;
        this.instruction = instruction;
        this.requestPayloadJson = requestPayloadJson;
        this.createdBy = createdBy;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public void markRunning() {
        status = AiGenerationJobStatus.RUNNING;
        errorCode = null;
        errorMessage = null;
        completedAt = null;
    }

    public void markSucceeded(String providerName, String modelName) {
        this.status = AiGenerationJobStatus.SUCCEEDED;
        this.providerName = providerName;
        this.modelName = modelName;
        this.completedAt = OffsetDateTime.now();
    }

    public void markFailed(String errorCode, String errorMessage) {
        this.status = AiGenerationJobStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage == null ? null : errorMessage.substring(0, Math.min(512, errorMessage.length()));
        this.completedAt = OffsetDateTime.now();
    }

    public void incrementRetry() {
        retryCount++;
    }

    public Long getId() {
        return id;
    }

    public AiGenerationJobStatus getStatus() {
        return status;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getModelName() {
        return modelName;
    }

    public Topic getTopic() {
        return topic;
    }

    public AtomicSkill getAtomicSkill() {
        return atomicSkill;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public int getRequestedCount() {
        return requestedCount;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public AiLanguageMode getLanguageMode() {
        return languageMode;
    }

    public String getInstruction() {
        return instruction;
    }

    public String getRequestPayloadJson() {
        return requestPayloadJson;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }
}
