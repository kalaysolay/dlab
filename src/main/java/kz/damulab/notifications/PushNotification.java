package kz.damulab.notifications;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import kz.damulab.users.AppUser;

@Entity
@Table(name = "push_notifications")
public class PushNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String text;

    @Column(name = "scheduled_at_utc", nullable = false)
    private OffsetDateTime scheduledAtUtc;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_screen", nullable = false, length = 64)
    private PushTargetScreen targetScreen;

    @Column(name = "target_payload_json", nullable = false, columnDefinition = "text")
    private String targetPayloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PushNotificationStatus status = PushNotificationStatus.SCHEDULED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "failure_code", length = 128)
    private String failureCode;

    @Column(name = "failure_message", length = 512)
    private String failureMessage;

    protected PushNotification() {
    }

    public PushNotification(
            String text,
            OffsetDateTime scheduledAtUtc,
            PushTargetScreen targetScreen,
            String targetPayloadJson,
            AppUser createdBy
    ) {
        this.text = text;
        this.scheduledAtUtc = scheduledAtUtc;
        this.targetScreen = targetScreen;
        this.targetPayloadJson = targetPayloadJson;
        this.createdBy = createdBy;
    }

    public void update(
            String text,
            OffsetDateTime scheduledAtUtc,
            PushTargetScreen targetScreen,
            String targetPayloadJson
    ) {
        requireScheduled();
        this.text = text;
        this.scheduledAtUtc = scheduledAtUtc;
        this.targetScreen = targetScreen;
        this.targetPayloadJson = targetPayloadJson;
        this.updatedAt = OffsetDateTime.now();
    }

    public void cancel() {
        requireScheduled();
        this.status = PushNotificationStatus.CANCELLED;
        this.cancelledAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public void markSent(OffsetDateTime sentAt) {
        requireScheduled();
        this.status = PushNotificationStatus.SENT;
        this.sentAt = sentAt;
        this.updatedAt = sentAt;
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void markFailed(String failureCode, String failureMessage, OffsetDateTime failedAt) {
        requireScheduled();
        this.status = PushNotificationStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = truncate(failureMessage, 512);
        this.updatedAt = failedAt;
    }

    private void requireScheduled() {
        if (status != PushNotificationStatus.SCHEDULED) {
            throw new PushNotificationException("push_not_editable");
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.substring(0, Math.min(max, value.length()));
    }

    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public OffsetDateTime getScheduledAtUtc() {
        return scheduledAtUtc;
    }

    public PushTargetScreen getTargetScreen() {
        return targetScreen;
    }

    public String getTargetPayloadJson() {
        return targetPayloadJson;
    }

    public PushNotificationStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }
}
