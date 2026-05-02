package kz.damulab.gamification;

import java.util.List;

public record StudentDashboardView(
        Long studentId,
        String fullName,
        String preferredLanguage,
        DailyMissionView mission,
        StreakView streak,
        LastActivityView lastActivity,
        ProgressWidgetView progress,
        List<AchievementView> achievements
) {
}
