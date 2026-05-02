package kz.damulab.quiz;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class QuizRoomEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public QuizRoomEventPublisher(SimpMessagingTemplate messagingTemplate, Clock clock) {
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    public void publish(String type, QuizRoom room) {
        if (room == null || room.getCode() == null) {
            return;
        }
        messagingTemplate.convertAndSend(
                "/topic/quiz.rooms." + room.getCode(),
                new QuizRoomEvent(
                        type,
                        room.getCode(),
                        room.getStatus().name().toLowerCase(Locale.ROOT),
                        OffsetDateTime.now(clock)
                )
        );
    }
}
