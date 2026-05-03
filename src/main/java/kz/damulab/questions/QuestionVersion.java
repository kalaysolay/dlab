package kz.damulab.questions;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import kz.damulab.content.AtomicSkill;
import kz.damulab.content.Subject;
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
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @OneToMany(mappedBy = "questionVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuestionVersionTopic> topicLinks = new ArrayList<>();

    @OneToMany(mappedBy = "questionVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuestionVersionGrade> gradeLinks = new ArrayList<>();

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
            Subject subject,
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
        this.subject = subject;
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
        this.subject = replacement.subject;
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

    public Topic getPrimaryTopic() {
        return topicLinks.stream()
                .filter(QuestionVersionTopic::isPrimaryTopic)
                .map(QuestionVersionTopic::getTopic)
                .findFirst()
                .or(() -> topicLinks.stream().min(Comparator.comparing(l -> l.getTopic().getId())).map(QuestionVersionTopic::getTopic))
                .orElse(null);
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

    public Subject getSubject() {
        return subject;
    }

    public List<QuestionVersionTopic> getTopicLinks() {
        return topicLinks;
    }

    public List<QuestionVersionGrade> getGradeLinks() {
        return gradeLinks;
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
