package kz.damulab.notifications;

import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PushNotificationResponse(
        Long id,
        String text,
        @JsonProperty("scheduled_at")
        String scheduledAt,
        @JsonProperty("scheduled_at_utc")
        OffsetDateTime scheduledAtUtc,
        @JsonProperty("target_screen")
        String targetScreen,
        String targetScreenTitle,
        @JsonProperty("target_payload")
        Map<String, Object> targetPayload,
        String status,
        String serverTimeZone,
        String serverTimeLabel,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime sentAt,
        OffsetDateTime cancelledAt,
        String failureCode,
        String failureMessage
) {
}
