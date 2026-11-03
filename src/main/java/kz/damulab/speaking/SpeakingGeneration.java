package kz.damulab.speaking;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Долговечный lease генерации, без текста ребёнка; защищает повторы и разные вкладки между транзакциями. */
@Entity @Table(name="speaking_generations")
class SpeakingGeneration {
    @Id UUID id=UUID.randomUUID();
    @Column(name="student_profile_id",nullable=false) Long studentId;
    @Column(name="session_id",nullable=false) UUID sessionId;
    @Column(name="request_id",nullable=false) UUID requestId;
    @Column(name="request_hash",nullable=false,length=64) String requestHash;
    @Column(name="session_version",nullable=false) long sessionVersion;
    @Column(nullable=false,length=16) String state="PROCESSING";
    @Column(name="active_slot") Integer activeSlot=1;
    @Column(name="created_at",nullable=false) OffsetDateTime createdAt;
    @Column(name="expires_at",nullable=false) OffsetDateTime expiresAt;
    protected SpeakingGeneration(){}
    void close(String status){state=status;activeSlot=null;}
}
