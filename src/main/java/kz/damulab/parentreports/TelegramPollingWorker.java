package kz.damulab.parentreports;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Один long-polling получатель на бота. В проде вместо него можно включить webhook. */
@Component
public class TelegramPollingWorker {
    private static final Logger log = LoggerFactory.getLogger(TelegramPollingWorker.class);
    private final ParentTelegramService service;
    private long offset;
    private Instant retryAt = Instant.MIN;

    public TelegramPollingWorker(ParentTelegramService service) { this.service = service; }

    /** Подтверждаем событие offset-ом только после успешной обработки и фиксации в БД. */
    @Scheduled(scheduler="parentTelegramPollingScheduler", fixedDelayString="${damulab.telegram.polling-delay-ms:1000}",
            initialDelayString="${damulab.telegram.initial-delay-ms:10000}")
    public void poll() {
        if (!service.config.available() || !service.config.polling() || service.clock.instant().isBefore(retryAt)) return;
        try {
            for (var update : service.telegram.updates(offset)) {
                if (!update.path("update_id").isIntegralNumber()) throw new IllegalStateException("invalid_update");
                service.update(update);
                offset = update.path("update_id").asLong() + 1;
            }
            // После рестарта offset=0 заберёт только ещё не подтверждённые серверу события.
            // Таблица update_id защищает от повторного действия последнего обработанного события.
        } catch (TelegramClient.Failure ex) {
            retryAt = service.clock.instant().plusSeconds(Math.max(5, ex.retrySeconds));
            log.warn("Telegram polling: {}. При 409 остановите второй poller или удалите webhook.", ex.code);
        } catch (Exception ex) {
            retryAt = service.clock.instant().plusSeconds(5);
            log.warn("Telegram polling: обработка события не завершена, будет повтор ({})", ex.getClass().getSimpleName());
        }
    }
}
