package kz.damulab.questions;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "question_flags")
public class QuestionFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_version_id")
    private QuestionVersion questionVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QuestionFlagSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QuestionFlagStatus status = QuestionFlagStatus.OPEN;

    @Column(nullable = false, length = 512)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    protected QuestionFlag() {
    }

    public QuestionFlag(Question question, QuestionFlagSource source, String reason) {
        this.question = question;
        this.questionVersion = question.getCurrentVersion();
        this.source = source;
        this.reason = reason;
    }

    public void resolve() {
        this.status = QuestionFlagStatus.RESOLVED;
        this.resolvedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Question getQuestion() {
        return question;
    }

    public QuestionVersion getQuestionVersion() {
        return questionVersion;
    }

    public QuestionFlagSource getSource() {
        return source;
    }

    public QuestionFlagStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }
}
