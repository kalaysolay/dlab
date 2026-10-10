package kz.damulab.quiz;

import java.math.BigDecimal;

/** Строка старого рейтинга; достоверное время старых ответов отсутствует и остаётся null. */
public record QuizParticipantResultResponse(
        Long participantId,
        String displayName,
        boolean currentStudent,
        int totalQuestions,
        int correctAnswers,
        BigDecimal score,
        BigDecimal maxScore,
        int percent,
        Long totalResponseMs
) {
}
