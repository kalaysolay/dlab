package kz.damulab.notifications;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public class PushNotificationForm {

    @Size(max = 120)
    private String text;

    @JsonAlias("scheduled_at")
    private String scheduledAt;

    @JsonAlias("target_screen")
    private PushTargetScreen targetScreen = PushTargetScreen.QUIZ_CREATE_ROOM;

    @JsonAlias("target_payload")
    private Map<String, Object> targetPayload = new HashMap<>();

    private Long subjectId;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @JsonProperty("scheduled_at")
    public String getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(String scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    @JsonProperty("target_screen")
    public PushTargetScreen getTargetScreen() {
        return targetScreen;
    }

    public void setTargetScreen(PushTargetScreen targetScreen) {
        this.targetScreen = targetScreen;
    }

    @JsonProperty("target_payload")
    public Map<String, Object> getTargetPayload() {
        return targetPayload;
    }

    public void setTargetPayload(Map<String, Object> targetPayload) {
        this.targetPayload = targetPayload == null ? new HashMap<>() : new HashMap<>(targetPayload);
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }
}
