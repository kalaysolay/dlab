package kz.damulab.parentlink;

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
import kz.damulab.auth.EmailDeliveryAttemptStatus;

/** Аудит результата передачи письма-приглашения провайдеру без сохранения секретного токена. */
@Entity
@Table(name = "parent_link_delivery_attempts")
public class ParentLinkDeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitation_id", nullable = false)
    private ParentLinkInvitation invitation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EmailDeliveryAttemptStatus status;

    @Column(name = "recipient_email", nullable = false, length = 320)
    private String recipientEmail;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "attempted_at", nullable = false)
    private OffsetDateTime attemptedAt;

    @Column(name = "completed_at", nullable = false)
    private OffsetDateTime completedAt;

    protected ParentLinkDeliveryAttempt() {
    }

    public ParentLinkDeliveryAttempt(
            ParentLinkInvitation invitation,
            EmailDeliveryAttemptStatus status,
            String recipientEmail,
            String providerMessageId,
            Integer httpStatus,
            String errorCode,
            OffsetDateTime attemptedAt,
            OffsetDateTime completedAt
    ) {
        this.invitation = invitation;
        this.status = status;
        this.recipientEmail = recipientEmail;
        this.providerMessageId = providerMessageId;
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.attemptedAt = attemptedAt;
        this.completedAt = completedAt;
    }

    public EmailDeliveryAttemptStatus getStatus() {
        return status;
    }
}
