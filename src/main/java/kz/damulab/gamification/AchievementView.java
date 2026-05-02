package kz.damulab.gamification;

import java.time.OffsetDateTime;

public record AchievementView(
        String code,
        String title,
        String description,
        boolean earned,
        OffsetDateTime earnedAt,
        int requiredValue
) {
}
