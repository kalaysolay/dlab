package kz.damulab.speaking;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Учебные наблюдения доступны только сервисам разговорника; общая аналитика не пишет в эту таблицу. */
interface SpeakingObservationRepository extends JpaRepository<SpeakingObservation, UUID> {
    long deleteByExpiresAtLessThanEqual(OffsetDateTime now);
    List<SpeakingObservation> findBySessionIdOrderByCreatedAt(UUID sessionId);
    Optional<SpeakingObservation> findByIdAndStudentIdAndSessionId(UUID id, Long studentId, UUID sessionId);
    List<SpeakingObservation> findByStudentIdAndEvidenceAndCreatedAtGreaterThanEqualAndExpiresAtGreaterThan(
            Long studentId, String evidence, OffsetDateTime since, OffsetDateTime now);
}
