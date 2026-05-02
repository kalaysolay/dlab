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
@Table(name = "answer_evaluations")
public class AnswerEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_question_id", nullable = false, unique = true)
    private TestSessionQuestion sessionQuestion;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "points_awarded", nullable = false)
    private BigDecimal pointsAwarded;

    @Column(name = "details_json", nullable = false)
    private String detailsJson;

    @Column(name = "evaluated_at", nullable = false)
    private OffsetDateTime evaluatedAt = OffsetDateTime.now();

    protected AnswerEvaluation() {
    }

    public AnswerEvaluation(
            TestSessionQuestion sessionQuestion,
            boolean correct,
            BigDecimal pointsAwarded,
            String detailsJson
    ) {
        this.sessionQuestion = sessionQuestion;
        this.correct = correct;
        this.pointsAwarded = pointsAwarded;
        this.detailsJson = detailsJson;
    }

    public Long getId() {
        return id;
    }

    public TestSessionQuestion getSessionQuestion() {
        return sessionQuestion;
    }

    public boolean isCorrect() {
        return correct;
    }

    public BigDecimal getPointsAwarded() {
        return pointsAwarded;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public OffsetDateTime getEvaluatedAt() {
        return evaluatedAt;
    }
}
