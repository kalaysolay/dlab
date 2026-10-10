package kz.damulab.quiz;

import kz.damulab.quiz.catalog.*;
import jakarta.persistence.*;
import java.util.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_answers")
/** Компонент комнаты: nullable-поля сохраняют legacy, новые механики опираются на версию и снимки. */
public class QuizAnswer {

    @Column(name = "response_ms")
    private Long responseMs;

    @Column(name = "timed_out")
    private boolean timedOut;

    @Column(name = "skipped")
    private boolean skipped;


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private QuizRound round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private QuizParticipant participant;

    @Column(name = "answer_json", nullable = false)
    private String answerJson;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "points_awarded", nullable = false)
    private BigDecimal pointsAwarded;

    @Column(name = "answered_at", nullable = false)
    private OffsetDateTime answeredAt = OffsetDateTime.now();

    /** Возвращает responseMs. */
    public Long getResponseMs() { return responseMs; }

    /** Возвращает timedOut. */
    public boolean isTimedOut() { return timedOut; }

    /** Возвращает skipped. */
    public boolean isSkipped() { return skipped; }

    /** Однократно фиксирует время и skip/timeout; legacy-время остаётся null. */
    public void recordTiming(long responseMs, boolean timedOut, boolean skipped) {
        recordTiming(responseMs, timedOut, skipped, answeredAt);
    }

    /** Записывает серверную метку принятия отдельно от вклада skip/timeout в разрешение ничьей. */
    public void recordTiming(long responseMs, boolean timedOut, boolean skipped, OffsetDateTime acceptedAt) {
        if (this.responseMs != null) throw new IllegalStateException("Ответ уже зафиксирован");
        if (responseMs < 0 || (timedOut && skipped)) throw new IllegalArgumentException("Некорректные параметры времени");
        if ((timedOut || skipped) && (correct || pointsAwarded.signum() != 0))
            throw new IllegalArgumentException("Пропуск и таймаут не приносят баллов");
        Objects.requireNonNull(acceptedAt);
        this.responseMs = responseMs; this.timedOut = timedOut; this.skipped = skipped;
        this.answeredAt = acceptedAt;
    }

    protected QuizAnswer() {
    }

    public QuizAnswer(
            QuizRound round,
            QuizParticipant participant,
            String answerJson,
            boolean correct,
            BigDecimal pointsAwarded
    ) {
        this.round = round;
        this.participant = participant;
        this.answerJson = answerJson;
        this.correct = correct;
        this.pointsAwarded = pointsAwarded == null ? BigDecimal.ZERO : pointsAwarded;
    }

    /** Legacy допускает исправление; в новой игре первый ответ окончателен. */
    public void replace(String answerJson, boolean correct, BigDecimal pointsAwarded) {
        if (round.getRoom().getGameVersion() != null) throw new IllegalStateException("Ответ новой игры окончателен");
        this.answerJson = answerJson;
        this.correct = correct;
        this.pointsAwarded = pointsAwarded == null ? BigDecimal.ZERO : pointsAwarded;
        this.answeredAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public QuizRound getRound() {
        return round;
    }

    public QuizParticipant getParticipant() {
        return participant;
    }

    public String getAnswerJson() {
        return answerJson;
    }

    public boolean isCorrect() {
        return correct;
    }

    public BigDecimal getPointsAwarded() {
        return pointsAwarded;
    }

    public OffsetDateTime getAnsweredAt() {
        return answeredAt;
    }
}
