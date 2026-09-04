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
import jakarta.persistence.UniqueConstraint;

import kz.damulab.users.StudentProfile;

/**
 * Прогресс одного ученика по одной лекции.
 *
 * <p>Запись создаётся при первом открытии лекции. Успешная сдача контрольных
 * вопросов и завершение лекции разделены, потому что ученик подтверждает завершение
 * отдельной кнопкой после теста.</p>
 */
@Entity
@Table(
        name = "student_lecture_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_student_lecture_progress",
                columnNames = {"student_profile_id", "lecture_id"}
        )
)
public class StudentLectureProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_profile_id", nullable = false)
    private StudentProfile studentProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StudentLectureStatus status;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "checkpoint_passed_at")
    private OffsetDateTime checkpointPassedAt;

    @Column(name = "checkpoint_attempt_count", nullable = false)
    private int checkpointAttemptCount;

    protected StudentLectureProgress() {
    }

    /** Создаёт прогресс в момент первого открытия опубликованной лекции. */
    public StudentLectureProgress(StudentProfile studentProfile, Lecture lecture) {
        this.studentProfile = studentProfile;
        this.lecture = lecture;
        this.status = StudentLectureStatus.IN_PROGRESS;
        this.startedAt = OffsetDateTime.now();
    }

    /**
     * Регистрирует очередную попытку. Успешная попытка запоминается навсегда для
     * этой лекции, а неуспешная не сбрасывает ранее достигнутый результат.
     */
    public void registerCheckpointAttempt(boolean passed) {
        checkpointAttemptCount++;
        if (passed && checkpointPassedAt == null) {
            checkpointPassedAt = OffsetDateTime.now();
        }
    }

    /** Завершает изучение; повторный вызов не меняет уже сохранённую дату. */
    public void complete() {
        if (status == StudentLectureStatus.DONE) {
            return;
        }
        status = StudentLectureStatus.DONE;
        completedAt = OffsetDateTime.now();
    }

    /** Возвращает идентификатор записи прогресса. */
    public Long getId() {
        return id;
    }

    /** Возвращает ученика, которому принадлежит прогресс. */
    public StudentProfile getStudentProfile() {
        return studentProfile;
    }

    /** Возвращает лекцию, для которой хранится прогресс. */
    public Lecture getLecture() {
        return lecture;
    }

    /** Возвращает текущее состояние изучения. */
    public StudentLectureStatus getStatus() {
        return status;
    }

    /** Возвращает время первого открытия лекции. */
    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    /** Возвращает время завершения или {@code null}, если лекция ещё изучается. */
    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    /** Возвращает время первой успешной сдачи контрольных вопросов. */
    public OffsetDateTime getCheckpointPassedAt() {
        return checkpointPassedAt;
    }

    /** Возвращает число отправленных попыток теста. */
    public int getCheckpointAttemptCount() {
        return checkpointAttemptCount;
    }
}
