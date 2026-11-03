package kz.damulab.speaking;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Отдельный учебный факт модуля speaking: предложение исправления не равно оценке школьного mastery. */
@Entity
@Table(name="speaking_observations")
class SpeakingObservation {
    @Id UUID id = UUID.randomUUID();
    @Column(name="student_profile_id", nullable=false) Long studentId;
    @Column(name="session_id", nullable=false) UUID sessionId;
    @Column(name="turn_id", nullable=false) UUID turnId;
    @Column(name="skill_code", nullable=false, length=64) String skillCode;
    @Column(nullable=false, length=16) String kind = "error";
    @Column(nullable=false, length=200) String original;
    @Column(nullable=false, length=200) String corrected;
    @Column(nullable=false, length=300) String explanation;
    @Column(nullable=false, length=16) String evidence = "suggested";
    @Column(nullable=false, length=16) String source = "typed";
    @Column(nullable=false) boolean assisted;
    @Column(name="created_at", nullable=false) OffsetDateTime createdAt;
    @Column(name="expires_at", nullable=false) OffsetDateTime expiresAt;
    protected SpeakingObservation() {}
}
