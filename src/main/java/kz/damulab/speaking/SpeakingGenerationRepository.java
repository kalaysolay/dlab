package kz.damulab.speaking;

import java.util.*;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

/** Удаление беседы каскадно удаляет lease; его служебные данные сами истекают через три минуты. */
interface SpeakingGenerationRepository extends JpaRepository<SpeakingGeneration,UUID>{
    Optional<SpeakingGeneration> findBySessionIdAndRequestId(UUID sessionId,UUID requestId);
    Optional<SpeakingGeneration> findByStudentIdAndActiveSlot(Long studentId,Integer activeSlot);
    void deleteByExpiresAtLessThanEqual(OffsetDateTime now);
}
