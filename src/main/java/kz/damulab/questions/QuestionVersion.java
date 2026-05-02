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

import kz.damulab.content.AtomicSkill;
import kz.damulab.content.Topic;

@Entity
@Table(name = "question_versions")
public class QuestionVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 32)
    private QuestionType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atomic_skill_id")
    private AtomicSkill atomicSkill;

    @Column(nullable = false)
    private int difficulty;

    @Column(name = "body_ru", nullable = false)
    private String bodyRu;

    @Column(name = "body_kk", nullable = false)
    private String bodyKk;

    @Column(name = "explanation_ru")
    private String explanationRu;

    @Column(name = "explanation_kk")
    private String explanationKk;

    @Column(name = "mini_lecture_ru")
    private String miniLectureRu;

    @Column(name = "mini_lecture_kk")
    private String miniLectureKk;

    @Column(nullable = false, length = 512)
    private String source;

    @Column(name = "options_json")
    private String optionsJson;

    @Column(name = "answer_key_json", nullable = false)
    private String answerKeyJson;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected QuestionVersion() {
    }

    public QuestionVersion(
            Question question,
            int versionNo,
            QuestionType type,
            Topic topic,
            AtomicSkill atomicSkill,
            int difficulty,
            String bodyRu,
            String bodyKk,
            String explanationRu,
            String explanationKk,
            String miniLectureRu,
            String miniLectureKk,
            String source,
            String optionsJson,
            String answerKeyJson
    ) {
        this.question = question;
        this.versionNo = versionNo;
        this.type = type;
        this.topic = topic;
        this.atomicSkill = atomicSkill;
        this.difficulty = difficulty;
        this.bodyRu = bodyRu;
        this.bodyKk = bodyKk;
        this.explanationRu = explanationRu;
        this.explanationKk = explanationKk;
        this.miniLectureRu = miniLectureRu;
        this.miniLectureKk = miniLectureKk;
        this.source = source;
        this.optionsJson = optionsJson;
        this.answerKeyJson = answerKeyJson;
    }

    public void replaceContent(QuestionVersion replacement) {
        this.type = replacement.type;
        this.topic = replacement.topic;
        this.atomicSkill = replacement.atomicSkill;
        this.difficulty = replacement.difficulty;
        this.bodyRu = replacement.bodyRu;
        this.bodyKk = replacement.bodyKk;
        this.explanationRu = replacement.explanationRu;
        this.explanationKk = replacement.explanationKk;
        this.miniLectureRu = replacement.miniLectureRu;
        this.miniLectureKk = replacement.miniLectureKk;
        this.source = replacement.source;
        this.optionsJson = replacement.optionsJson;
        this.answerKeyJson = replacement.answerKeyJson;
    }

    public void markPublished() {
        this.publishedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Question getQuestion() {
        return question;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public QuestionType getType() {
        return type;
    }

    public Topic getTopic() {
        return topic;
    }

    public AtomicSkill getAtomicSkill() {
        return atomicSkill;
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

    public String getMiniLectureRu() {
        return miniLectureRu;
    }

    public String getMiniLectureKk() {
        return miniLectureKk;
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

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
