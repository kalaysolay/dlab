package kz.damulab.speaking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.OffsetDateTime;

/** Принятые ходы возвращаются целиком и по порядку, без обрезания предыдущих вопросов. */
interface SpeakingTurnRepository extends JpaRepository<SpeakingTurn, UUID> {
    List<SpeakingTurn> findBySessionIdOrderBySequenceNo(UUID sessionId);
    Optional<SpeakingTurn> findBySessionIdAndRequestId(UUID sessionId, UUID requestId);
    @Modifying(clearAutomatically=true, flushAutomatically=true)
    @Query("update SpeakingTurn t set t.learnerText='', t.reactionText='', t.questionText='' where t.createdAt<:cutoff and t.learnerText<>''")
    int clearExpiredText(@org.springframework.data.repository.query.Param("cutoff") OffsetDateTime cutoff);
}
