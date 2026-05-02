package kz.damulab.quiz;

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
public class QuizAnswer {

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

    public void replace(String answerJson, boolean correct, BigDecimal pointsAwarded) {
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
