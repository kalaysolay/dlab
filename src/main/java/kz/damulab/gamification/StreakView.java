package kz.damulab.gamification;

import java.time.LocalDate;

public record StreakView(
        int currentCount,
        int longestCount,
        LocalDate lastActivityDate
) {
}
