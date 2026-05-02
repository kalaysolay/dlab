package kz.damulab.testing;

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
@Table(name = "student_answers")
public class StudentAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_question_id", nullable = false, unique = true)
    private TestSessionQuestion sessionQuestion;

    @Column(name = "answer_json", nullable = false)
    private String answerJson;

    @Column(name = "answered_at", nullable = false)
    private OffsetDateTime answeredAt = OffsetDateTime.now();

    protected StudentAnswer() {
    }

    public StudentAnswer(TestSessionQuestion sessionQuestion, String answerJson) {
        this.sessionQuestion = sessionQuestion;
        this.answerJson = answerJson;
    }

    public void replaceAnswer(String answerJson) {
        this.answerJson = answerJson;
        this.answeredAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public TestSessionQuestion getSessionQuestion() {
        return sessionQuestion;
    }

    public String getAnswerJson() {
        return answerJson;
    }

    public OffsetDateTime getAnsweredAt() {
        return answeredAt;
    }
}
