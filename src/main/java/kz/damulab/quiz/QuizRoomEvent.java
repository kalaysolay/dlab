package kz.damulab.quiz;

import java.time.OffsetDateTime;

/** Общий сигнал перечитать HTTP: только тип, код, статус и серверная метка, без учебных/персональных данных. */
public record QuizRoomEvent(
        String type,
        String code,
        String status,
        OffsetDateTime occurredAt
) {
}
