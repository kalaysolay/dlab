package kz.damulab.gamification;

public record ProgressWidgetView(
        long completedTests,
        int averagePercent,
        int overallMastery,
        int earnedAchievements
) {
}
