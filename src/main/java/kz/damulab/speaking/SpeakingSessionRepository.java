package kz.damulab.speaking;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Все чтения выполняются с локальным владельцем; чужой UUID не открывает беседу. */
interface SpeakingSessionRepository extends JpaRepository<SpeakingSession, UUID> {
    Optional<SpeakingSession> findByIdAndStudentId(UUID id, Long studentId);
    Optional<SpeakingSession> findByStudentIdAndCreateRequestId(Long studentId, UUID requestId);
    Optional<SpeakingSession> findByStudentIdAndActiveSlot(Long studentId, Integer slot);
    List<SpeakingSession> findTop20ByStudentIdOrderByCreatedAtDesc(Long studentId);
    @Modifying(clearAutomatically=true, flushAutomatically=true)
    @Query("update SpeakingSession s set s.status='EXPIRED', s.activeSlot=null, s.revision=s.revision+1 where s.status='ACTIVE' and s.updatedAt<:cutoff")
    int expireIdle(@org.springframework.data.repository.query.Param("cutoff") OffsetDateTime cutoff);
    @Modifying(clearAutomatically=true, flushAutomatically=true)
    @Query("delete from SpeakingSession s where s.createdAt<:cutoff and s.status<>'ACTIVE' and not exists (select o.id from SpeakingObservation o where o.sessionId=s.id)")
    int deleteExpiredMetadata(@org.springframework.data.repository.query.Param("cutoff") OffsetDateTime cutoff);
}
