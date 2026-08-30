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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import kz.damulab.users.AppUser;

/**
 * Одноразовый токен подтверждения email.
 *
 * <p>В БД хранится только SHA-256 хеш. Поэтому утечка таблицы не даёт готовых ссылок для
 * активации аккаунтов. У пользователя может быть только один актуальный токен.</p>
 */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EmailVerificationStatus status;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "resend_available_at", nullable = false)
    private OffsetDateTime resendAvailableAt;

    protected EmailVerificationToken() {
    }

    public EmailVerificationToken(
            AppUser user,
            String tokenHash,
            OffsetDateTime expiresAt,
            OffsetDateTime createdAt,
            OffsetDateTime resendAvailableAt
    ) {
        this.user = user;
        this.createdAt = createdAt;
        refresh(tokenHash, expiresAt, createdAt, resendAvailableAt);
    }

    /** Заменяет прежнюю ссылку новой при повторной отправке письма. */
    public void refresh(
            String tokenHash,
            OffsetDateTime expiresAt,
            OffsetDateTime updatedAt,
            OffsetDateTime resendAvailableAt
    ) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.status = EmailVerificationStatus.PENDING;
        this.updatedAt = updatedAt;
        this.activatedAt = null;
        this.resendAvailableAt = resendAvailableAt;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public EmailVerificationStatus getStatus() {
        return status;
    }

    public OffsetDateTime getActivatedAt() {
        return activatedAt;
    }

    public OffsetDateTime getResendAvailableAt() {
        return resendAvailableAt;
    }

    public boolean canResendAt(OffsetDateTime now) {
        return !resendAvailableAt.isAfter(now);
    }

    public void markVerified(OffsetDateTime now) {
        status = EmailVerificationStatus.VERIFIED;
        activatedAt = now;
        updatedAt = now;
    }

    public void markExpired(OffsetDateTime now) {
        status = EmailVerificationStatus.EXPIRED;
        updatedAt = now;
    }

    public boolean isExpiredAt(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }
}
