package kz.damulab.gamification;

import java.time.LocalDate;
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

import kz.damulab.users.StudentProfile;

@Entity
@Table(name = "streaks")
public class Streak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_profile_id", nullable = false, unique = true)
    private StudentProfile studentProfile;

    @Column(name = "current_count", nullable = false)
    private int currentCount;

    @Column(name = "longest_count", nullable = false)
    private int longestCount;

    @Column(name = "last_activity_date")
    private LocalDate lastActivityDate;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected Streak() {
    }

    public Streak(StudentProfile studentProfile) {
        this.studentProfile = studentProfile;
    }

    public void recordActivity(LocalDate activityDate, OffsetDateTime updatedAt) {
        if (activityDate == null) {
            return;
        }
        if (activityDate.equals(lastActivityDate)) {
            this.updatedAt = updatedAt;
            return;
        }
        if (lastActivityDate != null && activityDate.equals(lastActivityDate.plusDays(1))) {
            currentCount++;
        } else {
            currentCount = 1;
        }
        longestCount = Math.max(longestCount, currentCount);
        lastActivityDate = activityDate;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public StudentProfile getStudentProfile() {
        return studentProfile;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public int getLongestCount() {
        return longestCount;
    }

    public LocalDate getLastActivityDate() {
        return lastActivityDate;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
