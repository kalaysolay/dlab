package kz.damulab.testing;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_results")
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private TestSession session;

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

    protected TestResult() {
    }

    public TestResult(
            TestSession session,
            int totalQuestions,
            int correctAnswers,
            BigDecimal score,
            BigDecimal maxScore,
            int percent
    ) {
        this.session = session;
        this.totalQuestions = totalQuestions;
        this.correctAnswers = correctAnswers;
        this.score = score;
        this.maxScore = maxScore;
        this.percent = percent;
    }

    public Long getId() {
        return id;
    }

    public TestSession getSession() {
        return session;
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
