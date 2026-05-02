package kz.damulab.quiz;

import java.math.BigDecimal;

public record QuizParticipantResultResponse(
        Long participantId,
        String displayName,
        boolean currentStudent,
        int totalQuestions,
        int correctAnswers,
        BigDecimal score,
        BigDecimal maxScore,
        int percent
) {
}
