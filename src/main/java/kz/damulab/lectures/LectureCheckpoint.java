package kz.damulab.lectures;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import kz.damulab.questions.QuestionVersion;

@Entity
@Table(name = "lecture_checkpoints")
public class LectureCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_version_id", nullable = false)
    private LectureVersion lectureVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_version_id", nullable = false)
    private QuestionVersion questionVersion;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_mode", nullable = false, length = 32)
    private LectureControlMode selectionMode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected LectureCheckpoint() {
    }

    public LectureCheckpoint(
            LectureVersion lectureVersion,
            QuestionVersion questionVersion,
            int sortOrder,
            LectureControlMode selectionMode
    ) {
        this.lectureVersion = lectureVersion;
        this.questionVersion = questionVersion;
        this.sortOrder = sortOrder;
        this.selectionMode = selectionMode;
    }

    public Long getId() {
        return id;
    }

    public LectureVersion getLectureVersion() {
        return lectureVersion;
    }

    public QuestionVersion getQuestionVersion() {
        return questionVersion;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public LectureControlMode getSelectionMode() {
        return selectionMode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
