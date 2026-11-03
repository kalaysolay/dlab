package kz.damulab.speaking;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

/** Все пользовательские чтения ограничены владельцем; TTL не зависит от включённости интерфейса. */
interface SpeakingVoiceAttemptRepository extends JpaRepository<SpeakingVoiceAttempt,UUID> {
    Optional<SpeakingVoiceAttempt> findBySessionIdAndRequestId(UUID sessionId,UUID requestId);
    Optional<SpeakingVoiceAttempt> findByIdAndStudentIdAndSessionId(UUID id,Long studentId,UUID sessionId);
    Optional<SpeakingVoiceAttempt> findByStudentIdAndActiveSlot(Long studentId,Integer slot);
    void deleteByExpiresAtLessThanEqual(OffsetDateTime now);
}
