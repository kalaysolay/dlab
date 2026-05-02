package kz.damulab.quiz;

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
public class QuizRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private QuizRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_version_id", nullable = false)
    private QuestionVersion questionVersion;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(nullable = false)
    private BigDecimal points = BigDecimal.ONE;

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
