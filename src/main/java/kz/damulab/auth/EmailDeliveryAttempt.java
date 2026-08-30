package kz.damulab.auth;

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

/**
 * Нормализованный журнал отправки письма подтверждения.
 *
 * <p>Запись хранит полезные поля ответа SMTP.BZ ({@code result}, {@code to}, {@code messageid})
 * и безопасное описание сбоя. Сырой JSON, API-ключ, HTML письма и активационный токен сюда
 * никогда не попадают.</p>
 */
@Entity
@Table(name = "email_delivery_attempts")
public class EmailDeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verification_id", nullable = false)
    private EmailVerificationToken verification;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 64)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EmailDeliveryAttemptStatus status;

    @Column(name = "provider_result")
    private Boolean providerResult;

    @Column(name = "recipient_email", nullable = false, length = 320)
    private String recipientEmail;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false)
    private OffsetDateTime attemptedAt;

    @Column(name = "completed_at", nullable = false)
    private OffsetDateTime completedAt;

    protected EmailDeliveryAttempt() {
    }

    public EmailDeliveryAttempt(
            EmailVerificationToken verification,
            EmailDeliveryAttemptStatus status,
            Boolean providerResult,
            String recipientEmail,
            String providerMessageId,
            Integer httpStatus,
            String errorCode,
            String errorMessage,
            OffsetDateTime attemptedAt,
            OffsetDateTime completedAt
    ) {
        this.verification = verification;
        this.provider = "SMTP_BZ";
        this.purpose = "ACCOUNT_ACTIVATION";
        this.status = status;
        this.providerResult = providerResult;
        this.recipientEmail = recipientEmail;
        this.providerMessageId = providerMessageId;
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.attemptedAt = attemptedAt;
        this.completedAt = completedAt;
    }

    public Long getId() {
        return id;
    }

    public EmailDeliveryAttemptStatus getStatus() {
        return status;
    }

    public Boolean getProviderResult() {
        return providerResult;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
