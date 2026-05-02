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
import jakarta.persistence.Table;
import kz.damulab.questions.Question;
import kz.damulab.questions.QuestionType;
import kz.damulab.users.AppUser;

@Entity
@Table(name = "ai_generated_question_items")
public class AiGeneratedQuestionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private AiGeneratedQuestionBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 32)
    private AiGeneratedItemReviewStatus reviewStatus = AiGeneratedItemReviewStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 32)
    private QuestionType questionType;

    @Column(nullable = false)
    private int difficulty;

    @Column(name = "body_ru", nullable = false, columnDefinition = "text")
    private String bodyRu;

    @Column(name = "body_kk", nullable = false, columnDefinition = "text")
    private String bodyKk;

    @Column(name = "explanation_ru", columnDefinition = "text")
    private String explanationRu;

    @Column(name = "explanation_kk", columnDefinition = "text")
    private String explanationKk;

    @Column(nullable = false, length = 512)
    private String source;

    @Column(name = "options_json", columnDefinition = "text")
    private String optionsJson;

    @Column(name = "answer_key_json", nullable = false, columnDefinition = "text")
    private String answerKeyJson;

    @Column(name = "quality_score", nullable = false)
    private int qualityScore;

    @Column(name = "quality_notes", length = 512)
    private String qualityNotes;

    @Column(name = "flags_json", columnDefinition = "text")
    private String flagsJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_question_id")
    private Question createdQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private AppUser reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected AiGeneratedQuestionItem() {
    }

    public AiGeneratedQuestionItem(
            AiGeneratedQuestionBatch batch,
            AiGeneratedQuestionDraft draft,
            String optionsJson,
            String answerKeyJson,
            String flagsJson
    ) {
        this.batch = batch;
        this.questionType = draft.questionType();
        this.difficulty = draft.difficulty();
        this.bodyRu = draft.bodyRu();
        this.bodyKk = draft.bodyKk();
        this.explanationRu = draft.explanationRu();
        this.explanationKk = draft.explanationKk();
        this.source = draft.source();
        this.optionsJson = optionsJson;
        this.answerKeyJson = answerKeyJson;
        this.qualityScore = draft.qualityScore();
        this.qualityNotes = draft.qualityNotes();
        this.flagsJson = flagsJson;
    }

    public void edit(AiGeneratedQuestionItemEditForm form, AppUser reviewer) {
        this.bodyRu = form.getBodyRu().trim();
        this.bodyKk = form.getBodyKk().trim();
        this.explanationRu = trimToNull(form.getExplanationRu());
        this.explanationKk = trimToNull(form.getExplanationKk());
        this.source = form.getSource().trim();
        this.reviewStatus = AiGeneratedItemReviewStatus.EDITED;
        this.reviewedBy = reviewer;
        this.reviewedAt = OffsetDateTime.now();
    }

    public void approve(Question question, AppUser reviewer) {
        this.createdQuestion = question;
        this.reviewStatus = AiGeneratedItemReviewStatus.APPROVED;
        this.reviewedBy = reviewer;
        this.reviewedAt = OffsetDateTime.now();
    }

    public void delete(AppUser reviewer) {
        this.reviewStatus = AiGeneratedItemReviewStatus.DELETED;
        this.reviewedBy = reviewer;
        this.reviewedAt = OffsetDateTime.now();
    }

    public boolean isDeleted() {
        return reviewStatus == AiGeneratedItemReviewStatus.DELETED;
    }

    public Long getId() {
        return id;
    }

    public AiGeneratedQuestionBatch getBatch() {
        return batch;
    }

    public AiGeneratedItemReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public String getBodyRu() {
        return bodyRu;
    }

    public String getBodyKk() {
        return bodyKk;
    }

    public String getExplanationRu() {
        return explanationRu;
    }

    public String getExplanationKk() {
        return explanationKk;
    }

    public String getSource() {
        return source;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public String getAnswerKeyJson() {
        return answerKeyJson;
    }

    public int getQualityScore() {
        return qualityScore;
    }

    public String getQualityNotes() {
        return qualityNotes;
    }

    public String getFlagsJson() {
        return flagsJson;
    }

    public Question getCreatedQuestion() {
        return createdQuestion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
