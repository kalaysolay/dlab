package kz.damulab.speaking;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Очистка собственных данных модуля не зависит от флага доступа: выключение UI не отменяет сроки хранения. */
@Service
class SpeakingMaintenance {
    private final SpeakingSessionRepository sessions;
    private final SpeakingTurnRepository turns;
    private final SpeakingObservationRepository observations;
    private final SpeakingDailyUsageRepository usage;
    private final Clock clock;
    SpeakingMaintenance(SpeakingSessionRepository sessions, SpeakingTurnRepository turns,
            SpeakingObservationRepository observations, SpeakingDailyUsageRepository usage, Clock clock) {
        this.sessions=sessions; this.turns=turns; this.observations=observations; this.usage=usage; this.clock=clock;
    }
    /** Раз в час удаляет истёкшие цитаты и наблюдения; в логах нет содержимого или идентификаторов ребёнка. */
    @Scheduled(initialDelayString="3600000", fixedDelayString="3600000")
    @Transactional
    public void purge() {
        var now=OffsetDateTime.now(clock);
        sessions.expireIdle(now.minusMinutes(30));
        observations.deleteByExpiresAtLessThanEqual(now);
        turns.clearExpiredText(now.minusDays(30));
        // Ссылочные метаданные нужны до истечения наблюдений, но не дольше 180 дней.
        sessions.deleteExpiredMetadata(now.minusDays(180));
        usage.deleteByDayBefore(now.atZoneSameInstant(ZoneId.of("Asia/Qyzylorda")).toLocalDate().minusDays(30));
    }
}
