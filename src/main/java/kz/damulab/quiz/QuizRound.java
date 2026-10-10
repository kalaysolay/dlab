package kz.damulab.quiz;

import kz.damulab.quiz.catalog.*;
import jakarta.persistence.*;
import java.util.*;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import kz.damulab.questions.QuestionVersion;

@Entity
@Table(name = "quiz_rounds")
/** Компонент комнаты: nullable-поля сохраняют legacy, новые механики опираются на версию и снимки. */
public class QuizRound {

    @Enumerated(EnumType.STRING) @Column(name = "content_kind", length = 32)
    private QuizContentKind contentKind = QuizContentKind.QUESTION;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vocabulary_item_version_id")
    private VocabularyItemVersion vocabularyItemVersion;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "fact_statement_version_id")
    private FactStatementVersion factStatementVersion;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vocabulary_set_version_id")
    private VocabularySetVersion vocabularySetVersion;

    @Column(name = "prompt_snapshot", columnDefinition = "text", updatable = false)
    private String promptSnapshot;

    @Column(name = "options_snapshot_json", columnDefinition = "text", updatable = false)
    private String optionsSnapshotJson;

    @Column(name = "answer_key_snapshot_json", columnDefinition = "text", updatable = false)
    private String answerKeySnapshotJson;

    @Column(name = "explanation_snapshot", columnDefinition = "text", updatable = false)
    private String explanationSnapshot;

    @Column(name = "source_reference_snapshot", columnDefinition = "text", updatable = false)
    private String sourceReferenceSnapshot;

    @Column(name = "source_config_snapshot_json", columnDefinition = "text", updatable = false)
    private String sourceConfigSnapshotJson;

    @Column(name = "content_locale", length = 8, updatable = false)
    private String contentLocale;


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private QuizRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_version_id")
    private QuestionVersion questionVersion;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(nullable = false)
    private BigDecimal points = BigDecimal.ONE;

    /** Возвращает contentKind. */
    public QuizContentKind getContentKind() { return contentKind; }

    /** Возвращает vocabularyItemVersion. */
    public VocabularyItemVersion getVocabularyItemVersion() { return vocabularyItemVersion; }

    /** Возвращает factStatementVersion. */
    public FactStatementVersion getFactStatementVersion() { return factStatementVersion; }

    /** Возвращает vocabularySetVersion. */
    public VocabularySetVersion getVocabularySetVersion() { return vocabularySetVersion; }

    /** Возвращает promptSnapshot. */
    public String getPromptSnapshot() { return promptSnapshot; }

    /** Возвращает optionsSnapshotJson. */
    public String getOptionsSnapshotJson() { return optionsSnapshotJson; }

    /** Возвращает answerKeySnapshotJson; закрытый ключ нельзя включать в DTO текущего раунда. */
    public String getAnswerKeySnapshotJson() { return answerKeySnapshotJson; }

    /** Возвращает explanationSnapshot. */
    public String getExplanationSnapshot() { return explanationSnapshot; }

    /** Возвращает sourceReferenceSnapshot. */
    public String getSourceReferenceSnapshot() { return sourceReferenceSnapshot; }

    /** Возвращает sourceConfigSnapshotJson. */
    public String getSourceConfigSnapshotJson() { return sourceConfigSnapshotJson; }

    /** Возвращает contentLocale. */
    public String getContentLocale() { return contentLocale; }

    /** Создаёт словарный раунд без фиктивного предметного вопроса; провайдер добавляет снимок до сохранения. */
    public static QuizRound vocabulary(QuizRoom room, VocabularyItemVersion source, VocabularySetVersion setVersion, int orderNo) {
        QuizRound round = new QuizRound(room, null, orderNo, BigDecimal.ONE);
        round.contentKind = QuizContentKind.VOCABULARY; round.vocabularyItemVersion = source; round.vocabularySetVersion = setVersion;
        return round;
    }
    /** Создаёт раунд утверждения; boolean ключ остаётся в закрытом снимке. */
    public static QuizRound fact(QuizRoom room, FactStatementVersion source, int orderNo) {
        QuizRound round = new QuizRound(room, null, orderNo, BigDecimal.ONE);
        round.contentKind = QuizContentKind.FACT; round.factStatementVersion = source;
        return round;
    }
    /** Фиксирует снимок до первого сохранения; перезапись истории запрещена. */
    public void snapshot(String prompt, String optionsJson, String keyJson, String explanation, String source, String configJson, String locale) {
        if (id != null || promptSnapshot != null) throw new IllegalStateException("Снимок раунда уже зафиксирован");
        // Проверяем весь набор до присваивания: ошибочный вызов не должен оставлять
        // частичный снимок, который затем нельзя исправить повторным вызовом.
        if (prompt == null || optionsJson == null || keyJson == null || explanation == null
                || source == null || configJson == null || locale == null)
            throw new IllegalArgumentException("Снимок должен быть полным");
        promptSnapshot = prompt; optionsSnapshotJson = optionsJson; answerKeySnapshotJson = keyJson;
        explanationSnapshot = explanation; sourceReferenceSnapshot = source; sourceConfigSnapshotJson = configJson;
        contentLocale = locale;
    }

    protected QuizRound() {
    }

    public QuizRound(QuizRoom room, QuestionVersion questionVersion, int orderNo, BigDecimal points) {
        this.room = room;
        this.questionVersion = questionVersion;
        this.orderNo = orderNo;
        this.points = points == null ? BigDecimal.ONE : points;
    }

    public Long getId() {
        return id;
    }

    public QuizRoom getRoom() {
        return room;
    }

    public QuestionVersion getQuestionVersion() {
        return questionVersion;
    }

    public int getOrderNo() {
        return orderNo;
    }

    public BigDecimal getPoints() {
        return points;
    }
}
