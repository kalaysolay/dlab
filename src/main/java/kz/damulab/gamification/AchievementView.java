package kz.damulab.gamification;

import java.time.OffsetDateTime;

public record AchievementView(
        String code,
        String title,
        String description,
        boolean earned,
        OffsetDateTime earnedAt,
        int requiredValue,
        /** Текущее значение метрики (completed_tests или current_streak), обрезанное до цели для UI «N/M». */
        int progressCurrent
) {
}
