package kz.damulab.quiz;

import java.time.OffsetDateTime;
import java.util.List;

public record QuizRoomResponse(
        Long id,
        String code,
        String status,
        boolean host,
        Long currentParticipantId,
        Long subjectId,
        String subjectTitle,
        Long gradeId,
        String gradeTitle,
        String language,
        Integer difficulty,
        int questionCount,
        int roundSeconds,
        int maxPlayers,
        java.time.OffsetDateTime serverTime,
        Long activeRoundId,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        List<QuizParticipantResponse> participants,
        List<QuizRoundResponse> rounds
) {
}
