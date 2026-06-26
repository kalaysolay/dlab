package kz.damulab.notifications;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * VAPID-конфигурация для Web Push (RFC 8292).
 *
 * Ключи задаются через env-переменные VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY.
 * Генерация: npx web-push generate-vapid-keys (требует Node.js)
 * или openssl + ручное кодирование в Base64url.
 *
 * При пустых ключах активируется StubPushProvider.
 * Публичный ключ (vapidPublicKey) выставляется в meta[name="vapid-public-key"]
 * через VapidPublicKeyAdvice и используется клиентом в PushManager.subscribe().
 *
 * Свойства: damulab.push.vapid-public-key / vapid-private-key / subject
 */
@Component
@ConfigurationProperties(prefix = "damulab.push")
public class VapidProperties {

    /** Base64url-encoded EC public key (uncompressed). Передаётся браузеру. */
    private String vapidPublicKey = "";

    /** Base64url-encoded EC private key. Используется только на сервере при отправке. */
    private String vapidPrivateKey = "";

    /** mailto: или https: URI — идентификатор отправителя в VAPID JWT. */
    private String subject = "mailto:admin@damulab.kz";

    public String getVapidPublicKey() {
        return vapidPublicKey;
    }

    public void setVapidPublicKey(String vapidPublicKey) {
        this.vapidPublicKey = vapidPublicKey == null ? "" : vapidPublicKey;
    }

    public String getVapidPrivateKey() {
        return vapidPrivateKey;
    }

    public void setVapidPrivateKey(String vapidPrivateKey) {
        this.vapidPrivateKey = vapidPrivateKey == null ? "" : vapidPrivateKey;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    /** Проверяет, настроены ли оба VAPID-ключа (т.е. не пустые). */
    public boolean isConfigured() {
        return !vapidPublicKey.isBlank() && !vapidPrivateKey.isBlank();
    }
}
