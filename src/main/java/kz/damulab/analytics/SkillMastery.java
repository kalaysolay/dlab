package kz.damulab.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import kz.damulab.content.AtomicSkill;
import kz.damulab.content.Topic;
import kz.damulab.users.StudentProfile;

@Entity
@Table(name = "skill_mastery")
public class SkillMastery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_profile_id", nullable = false)
    private StudentProfile studentProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atomic_skill_id")
    private AtomicSkill atomicSkill;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "correct_answers", nullable = false)
    private int correctAnswers;

    @Column(name = "mastery_percent", nullable = false)
    private BigDecimal masteryPercent = BigDecimal.ZERO;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected SkillMastery() {
    }

    public SkillMastery(StudentProfile studentProfile, Topic topic, AtomicSkill atomicSkill) {
        this.studentProfile = studentProfile;
        this.topic = topic;
        this.atomicSkill = atomicSkill;
    }

    public void applyAttempt(int correct, int total, BigDecimal attemptPercent, int difficulty, OffsetDateTime attemptedAt) {
        BigDecimal weightedAttempt = attemptPercent.multiply(difficultyWeight(difficulty));
        BigDecimal nextMastery = attempts == 0
                ? weightedAttempt
                : masteryPercent.multiply(BigDecimal.valueOf(0.7)).add(weightedAttempt.multiply(BigDecimal.valueOf(0.3)));
        masteryPercent = clamp(nextMastery);
        attempts++;
        correctAnswers += correct;
        totalQuestions += total;
        lastAttemptAt = attemptedAt;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    private BigDecimal difficultyWeight(int difficulty) {
        int normalized = Math.max(1, Math.min(5, difficulty));
        return BigDecimal.valueOf(0.85 + normalized * 0.05);
    }

    private BigDecimal clamp(BigDecimal value) {
        BigDecimal clamped = value.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
        return clamped.setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public StudentProfile getStudentProfile() {
        return studentProfile;
    }

    public Topic getTopic() {
        return topic;
    }

    public AtomicSkill getAtomicSkill() {
        return atomicSkill;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public int getCorrectAnswers() {
        return correctAnswers;
    }

    public BigDecimal getMasteryPercent() {
        return masteryPercent;
    }

    public OffsetDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
