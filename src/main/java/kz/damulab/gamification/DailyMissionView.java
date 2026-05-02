package kz.damulab.gamification;

public record DailyMissionView(
        String title,
        String description,
        String actionText,
        String actionUrl
) {
}
