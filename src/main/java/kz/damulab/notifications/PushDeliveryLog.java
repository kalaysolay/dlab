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

@Entity
@Table(name = "push_delivery_logs")
public class PushDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "push_notification_id", nullable = false)
    private PushNotification pushNotification;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 32)
    private PushDeliveryStatus deliveryStatus;

    @Column(name = "provider_message_id", length = 128)
    private String providerMessageId;

    @Column(name = "error_code", length = 128)
    private String errorCode;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false)
    private OffsetDateTime attemptedAt = OffsetDateTime.now();

    protected PushDeliveryLog() {
    }

    public PushDeliveryLog(PushNotification pushNotification, PushDeliveryResult result, OffsetDateTime attemptedAt) {
        this.pushNotification = pushNotification;
        this.providerName = result.providerName();
        this.deliveryStatus = result.success() ? PushDeliveryStatus.SENT : PushDeliveryStatus.FAILED;
        this.providerMessageId = result.providerMessageId();
        this.errorCode = result.errorCode();
        this.errorMessage = truncate(result.errorMessage(), 512);
        this.attemptedAt = attemptedAt;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.substring(0, Math.min(max, value.length()));
    }
}
