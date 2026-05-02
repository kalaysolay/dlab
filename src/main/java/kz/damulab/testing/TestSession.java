package kz.damulab.testing;

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
@Table(name = "test_sessions")
public class TestSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_profile_id", nullable = false)
    private StudentProfile studentProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false, length = 32)
    private TestType testType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TestSessionStatus status = TestSessionStatus.IN_PROGRESS;

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

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt = OffsetDateTime.now();

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "time_limit_seconds", nullable = false)
    private int timeLimitSeconds;

    @Column(name = "settings_json", nullable = false)
    private String settingsJson = "{}";

    protected TestSession() {
    }

    public TestSession(
            StudentProfile studentProfile,
            TestType testType,
            Subject subject,
            Grade grade,
            String language,
            Integer difficulty,
            int timeLimitSeconds,
            String settingsJson
    ) {
        this.studentProfile = studentProfile;
        this.testType = testType;
        this.subject = subject;
        this.grade = grade;
        this.language = language;
        this.difficulty = difficulty;
        this.timeLimitSeconds = timeLimitSeconds;
        this.settingsJson = settingsJson == null || settingsJson.isBlank() ? "{}" : settingsJson;
    }

    public void finish() {
        if (status == TestSessionStatus.FINISHED) {
            return;
        }
        status = TestSessionStatus.FINISHED;
        finishedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public StudentProfile getStudentProfile() {
        return studentProfile;
    }

    public TestType getTestType() {
        return testType;
    }

    public TestSessionStatus getStatus() {
        return status;
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

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public String getSettingsJson() {
        return settingsJson;
    }
}
