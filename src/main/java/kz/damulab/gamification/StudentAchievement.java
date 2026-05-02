package kz.damulab.gamification;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import kz.damulab.users.StudentProfile;

@Entity
@Table(name = "student_achievements")
public class StudentAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_profile_id", nullable = false)
    private StudentProfile studentProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "achievement_id", nullable = false)
    private Achievement achievement;

    @Column(name = "earned_at", nullable = false)
    private OffsetDateTime earnedAt = OffsetDateTime.now();

    protected StudentAchievement() {
    }

    public StudentAchievement(StudentProfile studentProfile, Achievement achievement, OffsetDateTime earnedAt) {
        this.studentProfile = studentProfile;
        this.achievement = achievement;
        this.earnedAt = earnedAt;
    }

    public Long getId() {
        return id;
    }

    public StudentProfile getStudentProfile() {
        return studentProfile;
    }

    public Achievement getAchievement() {
        return achievement;
    }

    public OffsetDateTime getEarnedAt() {
        return earnedAt;
    }
}
