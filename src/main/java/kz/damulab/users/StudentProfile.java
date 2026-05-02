package kz.damulab.users;

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
@Table(name = "student_profiles")
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "grade_no")
    private Integer gradeNo;

    @Column(name = "preferred_language", nullable = false, length = 8)
    private String preferredLanguage = "ru";

    @Column(name = "lesson_reminders_enabled", nullable = false)
    private boolean lessonRemindersEnabled = true;

    @Column(name = "weekly_parent_report_enabled", nullable = false)
    private boolean weeklyParentReportEnabled = true;

    @Column(name = "session_result_push_enabled", nullable = false)
    private boolean sessionResultPushEnabled;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected StudentProfile() {
    }

    public StudentProfile(AppUser user, Integer gradeNo, String preferredLanguage) {
        this.user = user;
        this.gradeNo = gradeNo;
        this.preferredLanguage = normalizeLanguage(preferredLanguage);
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Integer getGradeNo() {
        return gradeNo;
    }

    public void update(Integer gradeNo, String preferredLanguage) {
        this.gradeNo = gradeNo;
        this.preferredLanguage = normalizeLanguage(preferredLanguage);
    }

    public void updateNotificationSettings(
            boolean lessonRemindersEnabled,
            boolean weeklyParentReportEnabled,
            boolean sessionResultPushEnabled
    ) {
        this.lessonRemindersEnabled = lessonRemindersEnabled;
        this.weeklyParentReportEnabled = weeklyParentReportEnabled;
        this.sessionResultPushEnabled = sessionResultPushEnabled;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public boolean isLessonRemindersEnabled() {
        return lessonRemindersEnabled;
    }

    public boolean isWeeklyParentReportEnabled() {
        return weeklyParentReportEnabled;
    }

    public boolean isSessionResultPushEnabled() {
        return sessionResultPushEnabled;
    }

    private String normalizeLanguage(String value) {
        return "kk".equals(value) ? "kk" : "ru";
    }
}
