package kz.damulab.quiz;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Публикует только безопасный сигнал перечитать HTTP; rollback не порождает событий. */
@Component
public class QuizRoomEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;
    private final java.util.concurrent.ConcurrentMap<String, Integer> announcedRounds = new java.util.concurrent.ConcurrentHashMap<>();

    /** Подключает транспорт и серверные часы. */
    public QuizRoomEventPublisher(SimpMessagingTemplate messagingTemplate, Clock clock) {
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    /** Снимает только код/статус и отправляет после commit, без JPA-графа, ответов и условий. */
    public void publish(String type, QuizRoom room) {
        if (room == null || room.getCode() == null) {
            return;
        }
        String destination = "/topic/quiz.rooms." + room.getCode();
        QuizRoomEvent event = new QuizRoomEvent(
                        type,
                        room.getCode(),
                        room.getStatus().name().toLowerCase(Locale.ROOT),
                        OffsetDateTime.now(clock)
                );
        Runnable send = () -> {
            if ("room.started".equals(type)) announcedRounds.put(event.code(), 1);
            if ("room.finished".equals(type) || "room.cancelled".equals(type) || "room.expired".equals(type)) announcedRounds.remove(event.code());
            messagingTemplate.convertAndSend(destination, event);
        };
        afterCommit(send);
    }
    /** Сообщает о новом серверном окне даже когда все ответили; после рестарта повтор сигнала безопасен. */
    public void roundChanged(QuizRoom room, int orderNo) {
        var event = new QuizRoomEvent("room.round_changed", room.getCode(), room.getStatus().name().toLowerCase(Locale.ROOT), OffsetDateTime.now(clock));
        afterCommit(() -> announcedRounds.compute(event.code(), (code, previous) -> {
            // Это только подавление повторных сигналов, расписание/finish всегда восстанавливаются из БД.
            if (previous == null || previous != orderNo) messagingTemplate.convertAndSend("/topic/quiz.rooms." + code, event);
            return orderNo;
        }));
    }
    private void afterCommit(Runnable send) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { send.run(); }
                    });
        } else send.run();
    }
}
