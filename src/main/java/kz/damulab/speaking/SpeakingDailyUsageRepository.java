package kz.damulab.speaking;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Счётчик меняется только под блокировкой профиля владельца. */
interface SpeakingDailyUsageRepository extends JpaRepository<SpeakingDailyUsage,Long> {
    long deleteByDayBefore(LocalDate cutoff);
    Optional<SpeakingDailyUsage> findByStudentIdAndDay(Long studentId, LocalDate day);
}
