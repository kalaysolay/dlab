package kz.damulab.testing;

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
@Table(name = "test_session_questions")
public class TestSessionQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private TestSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_version_id", nullable = false)
    private QuestionVersion questionVersion;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(nullable = false)
    private BigDecimal points = BigDecimal.ONE;

    protected TestSessionQuestion() {
    }

    public TestSessionQuestion(TestSession session, QuestionVersion questionVersion, int orderNo, BigDecimal points) {
        this.session = session;
        this.questionVersion = questionVersion;
        this.orderNo = orderNo;
        this.points = points == null ? BigDecimal.ONE : points;
    }

    public Long getId() {
        return id;
    }

    public TestSession getSession() {
        return session;
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
