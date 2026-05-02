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
@Table(name = "quiz_results")
public class QuizResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private QuizRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private QuizParticipant participant;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "correct_answers", nullable = false)
    private int correctAnswers;

    @Column(nullable = false)
    private BigDecimal score;

    @Column(name = "max_score", nullable = false)
    private BigDecimal maxScore;

    @Column(nullable = false)
    private int percent;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected QuizResult() {
    }

    public QuizResult(
            QuizRoom room,
            QuizParticipant participant,
            int totalQuestions,
            int correctAnswers,
            BigDecimal score,
            BigDecimal maxScore,
            int percent
    ) {
        this.room = room;
        this.participant = participant;
        this.totalQuestions = totalQuestions;
        this.correctAnswers = correctAnswers;
        this.score = score;
        this.maxScore = maxScore;
        this.percent = percent;
    }

    public Long getId() {
        return id;
    }

    public QuizRoom getRoom() {
        return room;
    }

    public QuizParticipant getParticipant() {
        return participant;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public int getCorrectAnswers() {
        return correctAnswers;
    }

    public BigDecimal getScore() {
        return score;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public int getPercent() {
        return percent;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
