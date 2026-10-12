package kz.damulab.quiz.catalog;

import jakarta.persistence.*;
import java.util.*;
import kz.damulab.questions.QuestionType;

/** Типовой источник версии SUBJECT_QUIZ. Составной FK в БД запрещает источник другой механики. */
@Entity @Table(name = "quiz_subject_game_configs")
public class QuizSubjectGameConfig {
    @Id @Column(name = "game_version_id")
    private Long gameVersionId;
    @MapsId @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "game_version_id")
    private QuizGameVersion gameVersion;
    @Enumerated(EnumType.STRING) @Column(name = "game_type", nullable = false, length = 32)
    private QuizGameType gameType = QuizGameType.SUBJECT_QUIZ;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "subject_id")
    private kz.damulab.content.Subject subject;
    @Column(name = "difficulty")
    private Integer difficulty;
    @ElementCollection @Enumerated(EnumType.STRING)
    @CollectionTable(name = "quiz_subject_game_question_types", joinColumns = @JoinColumn(name = "game_version_id"))
    @Column(name = "question_type", length = 32)
    private Set<QuestionType> questionTypes = new LinkedHashSet<>();
    protected QuizSubjectGameConfig() {}
    /** Связывает несохранённую конфигурацию с версией; тип и направление дополнительно проверяет FK. */
    public QuizSubjectGameConfig(QuizGameVersion gameVersion, kz.damulab.content.Subject subject, Integer difficulty) {
        this.gameVersion = gameVersion;
        this.subject = subject;
        this.difficulty = difficulty;
    }
    /** Возвращает идентификатор версии — одновременно PK конфигурации. */
    public Long getGameVersionId() { return gameVersionId; }
    /** Возвращает версию без неявного удаления или публикации. */
    public QuizGameVersion getGameVersion() { return gameVersion; }
    /** Возвращает настройку источника subject. */
    public kz.damulab.content.Subject getSubject() { return subject; }
    /** Возвращает настройку источника difficulty. */
    public Integer getDifficulty() { return difficulty; }
    /** Возвращает допустимые предметные типы; новые механики сюда не входят. */
    public Set<QuestionType> getQuestionTypes() { return Collections.unmodifiableSet(questionTypes); }
    /** Задаёт набор предметных типов для черновика. */
    public void setQuestionTypes(Set<QuestionType> value) { questionTypes = new LinkedHashSet<>(value); }
}
