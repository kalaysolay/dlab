package kz.damulab.notifications;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import kz.damulab.users.AppUser;

/**
 * Браузерная/устройственная подписка на push-уведомления.
 *
 * provider = "webpush": Web Push (RFC 8030), subscriptionJson содержит endpoint + ключи.
 * token_hash = SHA-256 hex от endpoint — используется для dedup-проверки при upsert.
 *
 * Таблица: device_tokens.
 */
@Entity
@Table(name = "device_tokens", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "token_hash"}))
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false, length = 32)
    private String platform;

    /** SHA-256 hex от endpoint (64 символа) — уникальный ключ подписки для dedup. */
    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    /**
     * Полный JSON браузерной PushSubscription (endpoint, keys.p256dh, keys.auth).
     * Используется WebPushProvider при отправке сообщения.
     */
    @Column(name = "subscription_json", columnDefinition = "text")
    private String subscriptionJson;

    /**
     * Тайм-зона устройства в формате IANA (например "Asia/Almaty", "Europe/Moscow").
     * Передаётся браузером при подписке через Intl.DateTimeFormat().resolvedOptions().timeZone.
     * Nullable: старые подписки не имеют этого поля; используется для информации о местном
     * времени пользователя (в будущем — для персонального расписания per-timezone).
     */
    @Column(length = 50)
    private String timezone;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected DeviceToken() {
    }

    /**
     * Создаёт новую запись Web Push подписки.
     *
     * @param user             пользователь-владелец подписки
     * @param provider         тип провайдера, например "webpush"
     * @param platform         платформа, например "browser"
     * @param tokenHash        SHA-256 hex от endpoint
     * @param subscriptionJson JSON браузерной PushSubscription
     */
    public DeviceToken(AppUser user, String provider, String platform,
                       String tokenHash, String subscriptionJson) {
        this.user = user;
        this.provider = provider;
        this.platform = platform;
        this.tokenHash = tokenHash;
        this.subscriptionJson = subscriptionJson;
    }

    public DeviceToken(AppUser user, String provider, String platform,
                       String tokenHash, String subscriptionJson, String timezone) {
        this(user, provider, platform, tokenHash, subscriptionJson);
        this.timezone = timezone;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getSubscriptionJson() {
        return subscriptionJson;
    }

    /**
     * Обновляет subscriptionJson и метку обновления при повторной регистрации того же endpoint.
     * Также обновляет timezone, если передана (могла измениться при смене региона устройства).
     */
    public void updateSubscription(String subscriptionJson, String timezone) {
        this.subscriptionJson = subscriptionJson;
        if (timezone != null && !timezone.isBlank()) {
            this.timezone = timezone;
        }
        this.updatedAt = OffsetDateTime.now();
    }

    /** @deprecated Используй {@link #updateSubscription(String, String)} с timezone. */
    @Deprecated
    public void updateSubscription(String subscriptionJson) {
        updateSubscription(subscriptionJson, null);
    }

    public String getTimezone() {
        return timezone;
    }
}
