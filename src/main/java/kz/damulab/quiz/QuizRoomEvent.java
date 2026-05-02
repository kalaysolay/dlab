package kz.damulab.quiz;

import java.time.OffsetDateTime;

public record QuizRoomEvent(
        String type,
        String code,
        String status,
        OffsetDateTime occurredAt
) {
}
