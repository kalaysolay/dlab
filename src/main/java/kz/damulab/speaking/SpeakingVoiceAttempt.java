package kz.damulab.speaking;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

/** Короткоживущий черновик ASR (15 минут), привязанный к владельцу и конкретному вопросу. */
@Entity
@Table(name="speaking_voice_attempts")
class SpeakingVoiceAttempt {
    @Id UUID id=UUID.randomUUID();
    @Column(name="student_profile_id",nullable=false) Long studentId;
    @Column(name="session_id",nullable=false) UUID sessionId;
    @Column(name="request_id",nullable=false) UUID requestId;
    @Column(name="session_version",nullable=false) long sessionVersion;
    @Column(name="audio_hash",nullable=false,length=64) String audioHash;
    @Column(nullable=false,length=16) String state="PROCESSING";
    @Column(name="active_slot") Integer activeSlot=1;
    @Column(length=2000) String transcript;
    @Column(name="audio_seconds",nullable=false) int audioSeconds;
    @Column(name="usage_day",nullable=false) LocalDate usageDay;
    @Column(name="created_at",nullable=false) OffsetDateTime createdAt;
    @Column(name="expires_at",nullable=false) OffsetDateTime expiresAt;
    protected SpeakingVoiceAttempt() {}
    void close(String status) { state=status; activeSlot=null; }
}
