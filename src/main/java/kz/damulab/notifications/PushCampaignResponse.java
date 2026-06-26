package kz.damulab.notifications;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTO ответа API для кампании с последними запусками.
 * Возвращается при GET/POST/PATCH /api/admin/push-campaigns.
 */
public record PushCampaignResponse(
        Long id,
        String name,
        String bodyTemplate,
        String targetScreen,
        String targetScreenTitle,
        String sendTime,
        String daysOfWeek,
        boolean enabled,
        OffsetDateTime lastRunAt,
        OffsetDateTime createdAt,
        List<RunSummary> recentRuns
) {

    /** Краткая статистика одного запуска для отображения в таблице. */
    public record RunSummary(
            Long id,
            OffsetDateTime triggeredAt,
            OffsetDateTime finishedAt,
            int devicesTargeted,
            int devicesSent,
            int devicesFailed
    ) {
    }
}
