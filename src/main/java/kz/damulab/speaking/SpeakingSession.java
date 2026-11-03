package kz.damulab.speaking;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Сессия разговорника; activeSlot=1 обеспечивает одну активную беседу на ученика на уровне БД. */
@Entity
@Table(name = "speaking_sessions")
class SpeakingSession {
    @Id UUID id = UUID.randomUUID();
    @Column(name="student_profile_id", nullable=false) Long studentId;
    @Column(name="create_request_id", nullable=false) UUID createRequestId;
    @Column(name="scenario_code", nullable=false, length=64) String scenarioCode;
    @Column(name="scenario_snapshot", nullable=false, columnDefinition="text") String scenarioSnapshot;
    @Column(nullable=false, length=2) String level;
    @Column(name="explanation_language", nullable=false, length=2) String language;
    @Column(name="provider_version", nullable=false, length=64) String providerVersion = "stub-v1";
    @Column(nullable=false, length=24) String status = "ACTIVE";
    @Column(name="active_slot") Integer activeSlot = 1;
    @Column(name="accepted_turns", nullable=false) int acceptedTurns;
    @Column(name="total_turns",nullable=false) int totalTurns;
    @Column(name="off_topic_count",nullable=false) int offTopicCount;
    @Column(name="assisted_next", nullable=false) boolean assistedNext;
    @Version @Column(nullable=false) long revision;
    @Column(name="created_at", nullable=false) OffsetDateTime createdAt;
    @Column(name="updated_at", nullable=false) OffsetDateTime updatedAt;
    protected SpeakingSession() {}
    void close(String reason, OffsetDateTime now) { status = reason; activeSlot = null; updatedAt = now; }
}
