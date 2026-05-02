package kz.damulab.notifications;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PushNotificationStatus {
    SCHEDULED("scheduled"),
    SENT("sent"),
    CANCELLED("cancelled"),
    FAILED("failed");

    private final String apiValue;

    PushNotificationStatus(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String apiValue() {
        return apiValue;
    }

    @JsonCreator
    public static PushNotificationStatus fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (PushNotificationStatus status : values()) {
            if (status.apiValue.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown push status: " + value);
    }
}
