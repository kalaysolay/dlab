package kz.damulab.gamification;

import java.time.OffsetDateTime;

public record LastActivityView(
        Long sessionId,
        String title,
        String detail,
        int percent,
        OffsetDateTime finishedAt
) {
}
