package kz.damulab.quiz;

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

import kz.damulab.content.Grade;
import kz.damulab.content.Subject;
import kz.damulab.users.StudentProfile;

@Entity
@Table(name = "quiz_rooms")
public class QuizRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_student_profile_id", nullable = false)
    private StudentProfile hostStudentProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    private Grade grade;

    @Column(nullable = false, length = 8)
    private String language;

    @Column
    private Integer difficulty;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "round_seconds", nullable = false)
    private int roundSeconds;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QuizRoomStatus status = QuizRoomStatus.WAITING;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    protected QuizRoom() {
    }

    public QuizRoom(
            String code,
            StudentProfile hostStudentProfile,
            Subject subject,
            Grade grade,
            String language,
            Integer difficulty,
            int questionCount,
            int roundSeconds,
            int maxPlayers
    ) {
        this.code = code;
        this.hostStudentProfile = hostStudentProfile;
        this.subject = subject;
        this.grade = grade;
        this.language = language;
        this.difficulty = difficulty;
        this.questionCount = questionCount;
        this.roundSeconds = roundSeconds;
        this.maxPlayers = maxPlayers;
    }

    public void start(OffsetDateTime now) {
        if (status != QuizRoomStatus.WAITING) {
            return;
        }
        status = QuizRoomStatus.ACTIVE;
        startedAt = now;
    }

    public void finish(OffsetDateTime now) {
        if (status == QuizRoomStatus.FINISHED) {
            return;
        }
        status = QuizRoomStatus.FINISHED;
        finishedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public StudentProfile getHostStudentProfile() {
        return hostStudentProfile;
    }

    public Subject getSubject() {
        return subject;
    }

    public Grade getGrade() {
        return grade;
    }

    public String getLanguage() {
        return language;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public int getRoundSeconds() {
        return roundSeconds;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public QuizRoomStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }
}
