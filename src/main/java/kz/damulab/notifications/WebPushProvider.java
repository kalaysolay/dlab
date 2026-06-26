package kz.damulab.notifications;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Security;

/**
 * Реализация PushProvider через Web Push (RFC 8030 + VAPID, RFC 8292).
 *
 * Использует nl.martijndwars:web-push + BouncyCastle для шифрования payload.
 * Активируется через PushProviderConfig только при наличии VAPID-ключей.
 *
 * При вызове send():
 *  - Загружает все активные webpush-подписки из device_tokens.
 *  - Для каждой формирует Web Push запрос с зашифрованным JSON payload.
 *  - Возвращает success, если хотя бы одна доставка прошла.
 *
 * Формат push payload: {"title": "Damulab", "body": "...", "url": "/student/..."}
 * service-worker.js обрабатывает его в push event → showNotification.
 */
public class WebPushProvider implements PushProvider {

    private static final Logger log = LoggerFactory.getLogger(WebPushProvider.class);

    private final PushService pushService;
    private final DeviceTokenRepository deviceTokens;
    private final ObjectMapper objectMapper;

    /**
     * @param vapid        VAPID-ключи из application.yml / env
     * @param deviceTokens репозиторий подписок для рассылки
     * @param objectMapper для сериализации push payload и парсинга subscription JSON
     * @throws Exception если VAPID-ключи некорректны
     */
    public WebPushProvider(VapidProperties vapid,
                           DeviceTokenRepository deviceTokens,
                           ObjectMapper objectMapper) throws Exception {
        // BouncyCastle нужен для EC-шифрования; добавляем один раз при создании bean
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        this.pushService = new PushService(
                vapid.getVapidPublicKey(),
                vapid.getVapidPrivateKey(),
                vapid.getSubject()
        );
        this.deviceTokens = deviceTokens;
        this.objectMapper = objectMapper;
    }

    @Override
    public PushDeliveryResult send(PushNotification notification) {
        List<DeviceToken> subscribers = deviceTokens.findByProviderAndEnabledTrue("webpush");

        if (subscribers.isEmpty()) {
            // Нет подписчиков — не ошибка, уведомление просто некуда доставить
            log.debug("webpush: no active subscribers, skipping notification {}", notification.getId());
            return PushDeliveryResult.sent("webpush", "no_subscribers");
        }

        String payload = buildPayload(notification);
        int sent = 0;

        for (DeviceToken token : subscribers) {
            Subscription sub = parseSubscription(token.getSubscriptionJson());
            if (sub == null) {
                log.warn("webpush: cannot parse subscription for device_token {}", token.getId());
                continue;
            }
            try {
                pushService.send(new Notification(sub, payload));
                sent++;
            } catch (Exception e) {
                // Логируем отдельную неудачу, продолжаем с остальными подписчиками
                log.warn("webpush: failed to push to token {}: {}", token.getId(), e.getMessage());
            }
        }

        if (sent > 0) {
            return PushDeliveryResult.sent("webpush", "broadcast:" + sent + "/" + subscribers.size());
        }
        return PushDeliveryResult.failed("webpush", "all_failed",
                "All " + subscribers.size() + " push send(s) failed");
    }

    /**
     * Отправляет push конкретному устройству с готовым текстом и URL.
     * Используется кампаниями — текст уже персонализирован (переменные подставлены).
     *
     * @param body      готовый текст уведомления
     * @param targetUrl относительный URL для перехода (например "/student/quiz-hub")
     * @param token     подписка устройства-получателя
     */
    @Override
    public PushDeliveryResult sendRaw(String body, String targetUrl, DeviceToken token) {
        Subscription sub = parseSubscription(token.getSubscriptionJson());
        if (sub == null) {
            log.warn("webpush sendRaw: cannot parse subscription for device_token {}", token.getId());
            return PushDeliveryResult.failed("webpush", "invalid_subscription", "Cannot parse subscription JSON");
        }
        String payload = buildRawPayload(body, targetUrl);
        try {
            pushService.send(new Notification(sub, payload));
            return PushDeliveryResult.sent("webpush", "token-" + token.getId());
        } catch (Exception e) {
            log.warn("webpush sendRaw: failed to push to token {}: {}", token.getId(), e.getMessage());
            return PushDeliveryResult.failed("webpush", "send_error", e.getMessage());
        }
    }

    /**
     * Строит JSON payload для push-уведомления.
     * Формат читается service-worker.js в обработчике push event.
     */
    private String buildPayload(PushNotification notification) {
        String targetUrl = switch (notification.getTargetScreen()) {
            case QUIZ_CREATE_ROOM -> "/student/quiz-hub";
            case SUBJECT_TEST -> buildSubjectTestUrl(notification.getTargetPayloadJson());
        };
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "title", "Damulab",
                    "body", notification.getText(),
                    "url", targetUrl
            ));
        } catch (JsonProcessingException e) {
            // Fallback: вручную собранный JSON (без спецсимволов в тексте)
            return "{\"title\":\"Damulab\",\"body\":\"Новое уведомление\",\"url\":\"/\"}";
        }
    }

    /**
     * Строит JSON payload для sendRaw (кампании): принимает готовый body и targetUrl.
     */
    private String buildRawPayload(String body, String targetUrl) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "title", "Damulab",
                    "body", body != null ? body : "",
                    "url", targetUrl != null ? targetUrl : "/"
            ));
        } catch (JsonProcessingException e) {
            return "{\"title\":\"Damulab\",\"body\":\"Новое уведомление\",\"url\":\"/\"}";
        }
    }

    /**
     * Извлекает subject_id из target_payload_json и формирует URL страницы теста.
     */
    private String buildSubjectTestUrl(String targetPayloadJson) {
        try {
            var node = objectMapper.readTree(targetPayloadJson);
            var subjectId = node.path("subject_id");
            if (!subjectId.isMissingNode()) {
                return "/student/tests?subjectId=" + subjectId.asLong();
            }
        } catch (Exception e) {
            log.debug("webpush: cannot parse subject_id from payload: {}", e.getMessage());
        }
        return "/student/tests";
    }

    /**
     * Десериализует хранимый subscription JSON в объект Subscription для nl.martijndwars.webpush.
     * Ожидаемый формат: {"endpoint":"...","keys":{"p256dh":"...","auth":"..."}}
     */
    private Subscription parseSubscription(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            var node = objectMapper.readTree(json);
            String endpoint = node.path("endpoint").asText();
            String p256dh   = node.path("keys").path("p256dh").asText();
            String auth     = node.path("keys").path("auth").asText();
            if (endpoint.isEmpty() || p256dh.isEmpty() || auth.isEmpty()) return null;
            return new Subscription(endpoint, new Subscription.Keys(p256dh, auth));
        } catch (Exception e) {
            log.warn("webpush: failed to parse subscription JSON: {}", e.getMessage());
            return null;
        }
    }
}
