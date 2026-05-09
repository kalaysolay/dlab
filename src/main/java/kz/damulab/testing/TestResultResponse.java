package kz.damulab.testing;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import kz.damulab.gamification.AchievementUnlockPayload;

public record TestResultResponse(
        Long resultId,
        Long sessionId,
        int totalQuestions,
        int correctAnswers,
        BigDecimal score,
        BigDecimal maxScore,
        int percent,
        OffsetDateTime createdAt,
        List<QuestionResultResponse> questions,
        /** Пустой при повторном открытии результата; заполняется только при первичном завершении сессии на сервере. */
        List<AchievementUnlockPayload> newlyUnlockedAchievements
) {
}
