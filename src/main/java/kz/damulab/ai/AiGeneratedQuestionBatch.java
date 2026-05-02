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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_generated_question_batches")
public class AiGeneratedQuestionBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private AiGenerationJob job;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiGeneratedQuestionBatchStatus status = AiGeneratedQuestionBatchStatus.REVIEW;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected AiGeneratedQuestionBatch() {
    }

    public AiGeneratedQuestionBatch(AiGenerationJob job) {
        this.job = job;
    }

    public Long getId() {
        return id;
    }

    public AiGenerationJob getJob() {
        return job;
    }

    public AiGeneratedQuestionBatchStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
