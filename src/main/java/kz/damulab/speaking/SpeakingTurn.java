package kz.damulab.speaking;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Принятый ответ и следующая реплика ИИ; исходный запрос не выводится в технические логи. */
@Entity
@Table(name="speaking_turns")
class SpeakingTurn {
    @Id UUID id = UUID.randomUUID();
    @Column(name="session_id", nullable=false) UUID sessionId;
    @Column(name="request_id", nullable=false) UUID requestId;
    @Column(name="sequence_no", nullable=false) int sequenceNo;
    @Column(nullable=false, length=16) String source = "typed";
    @Column(name="voice_attempt_id") UUID voiceAttemptId;
    @Column(name="audio_seconds",nullable=false) int audioSeconds;
    @Column(name="learner_text", nullable=false, length=2000) String learnerText;
    @Column(name="reaction_text", nullable=false, length=240) String reactionText;
    @Column(name="question_text", nullable=false, length=500) String questionText;
    @Column(name="created_at", nullable=false) OffsetDateTime createdAt;
    protected SpeakingTurn() {}
}
