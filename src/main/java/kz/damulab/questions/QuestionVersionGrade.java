package kz.damulab.questions;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import kz.damulab.content.Grade;

@Entity
@Table(
        name = "question_version_grades",
        uniqueConstraints = @UniqueConstraint(columnNames = {"question_version_id", "grade_id"})
)
public class QuestionVersionGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_version_id", nullable = false)
    private QuestionVersion questionVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;

    protected QuestionVersionGrade() {
    }

    public QuestionVersionGrade(QuestionVersion questionVersion, Grade grade) {
        this.questionVersion = questionVersion;
        this.grade = grade;
    }

    public Long getId() {
        return id;
    }

    public QuestionVersion getQuestionVersion() {
        return questionVersion;
    }

    public Grade getGrade() {
        return grade;
    }
}
