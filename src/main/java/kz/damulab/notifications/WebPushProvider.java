package kz.damulab.notifications;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
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
            // INFO нужен для диагностики админских push: статус sent + no_subscribers означает,
            // что worker отработал, но ни одно устройство ещё не подписалось на Web Push.
            log.info("webpush broadcast skipped: notificationId={} reason=no_active_subscribers",
                    notification.getId());
            return PushDeliveryResult.sent("webpush", "no_subscribers");
        }

        String payload = buildPayload(notification);
        int sent = 0;

        // statuses попадает и в application log, и в push_delivery_logs.provider_message_id.
        // Формат намеренно короткий: "<deviceTokenId>:<httpStatus|invalid|exception>".
        // Так можно быстро понять, принял ли внешний push-сервис запрос, не раскрывая endpoint и ключи подписки.
        List<String> statuses = new ArrayList<>();
        String lastFailure = null;
        log.info("webpush broadcast started: notificationId={} targetScreen={} subscribers={}",
                notification.getId(), notification.getTargetScreen().apiValue(), subscribers.size());

        for (DeviceToken token : subscribers) {
            Subscription sub = parseSubscription(token.getSubscriptionJson());
            if (sub == null) {
                // Битая/устаревшая запись в device_tokens: её нельзя отправить в push-сервис.
                // Полный subscription_json не логируем, потому что внутри есть endpoint и криптографические ключи.
                log.warn("webpush delivery invalid: notificationId={} deviceTokenId={} userId={} endpointHost={} reason=invalid_subscription_json",
                        notification.getId(), token.getId(), userId(token), endpointHost(token));
                statuses.add(token.getId() + ":invalid");
                lastFailure = "Invalid subscription JSON";
                continue;
            }
            try {
                // Важно проверять HTTP status. Раньше сам факт отсутствия exception считался success,
                // из-за чего в админке было broadcast:N/N даже при возможных 4xx от push-сервиса.
                // У Web Push успешная отправка означает 2xx-ответ от endpoint-а браузера/платформы.
                // Важно явно использовать AES128GCM. У web-push 5.1.2 метод send(Notification)
                // отправляет через старый AESGCM и кладёт VAPID public key в Crypto-Key header.
                // FCM в 2026 отклоняет такой запрос с 403:
                // "crypto-key header had invalid format ... p256ecdsa=base64(publicApplicationServerKey)".
                // AES128GCM формирует современный RFC 8291/RFC 8292 запрос и не ломается на этом header.
                HttpResponse response = pushService.send(new Notification(sub, payload), Encoding.AES128GCM);
                int statusCode = statusCode(response);
                statuses.add(token.getId() + ":" + statusCode);
                if (isSuccessfulPushStatus(statusCode)) {
                    sent++;
                    log.info("webpush delivery accepted: notificationId={} deviceTokenId={} userId={} endpointHost={} httpStatus={}",
                            notification.getId(), token.getId(), userId(token), endpointHost(token), statusCode);
                } else {
                    String responseDetails = responseDetails(response);
                    lastFailure = "Push service returned HTTP " + statusCode + " " + responseDetails;
                    log.warn("webpush delivery rejected: notificationId={} deviceTokenId={} userId={} endpointHost={} httpStatus={} response={}",
                            notification.getId(), token.getId(), userId(token), endpointHost(token), statusCode, responseDetails);
                }
            } catch (Exception e) {
                // Логируем отдельную неудачу, продолжаем с остальными подписчиками
                log.warn("webpush delivery exception: notificationId={} deviceTokenId={} userId={} endpointHost={} exception={} message={}",
                        notification.getId(), token.getId(), userId(token), endpointHost(token),
                        e.getClass().getSimpleName(), e.getMessage());
                statuses.add(token.getId() + ":exception");
                lastFailure = e.getMessage();
            }
        }

        if (sent > 0) {
            // Частичный success допустим: одно устройство могло принять push, другое вернуть 404/410.
            // Поэтому общий статус уведомления остаётся sent, а детализация уходит в statuses.
            log.info("webpush broadcast finished: notificationId={} attempted={} accepted={} failed={} statuses={}",
                    notification.getId(), subscribers.size(), sent, subscribers.size() - sent, String.join(",", statuses));
            return PushDeliveryResult.sent("webpush",
                    truncate("broadcast:" + sent + "/" + subscribers.size() + " statuses=" + String.join(",", statuses), 128));
        }
        // Если ни один push-сервис не принял сообщение 2xx-ответом, считаем отправку failed.
        // Это полезнее для админки, чем прежний broadcast:N/N при неудачных HTTP-ответах.
        log.warn("webpush broadcast failed: notificationId={} attempted={} accepted=0 failed={} statuses={} lastFailure={}",
                notification.getId(), subscribers.size(), subscribers.size(), String.join(",", statuses), lastFailure);
        return PushDeliveryResult.failed("webpush", "all_failed",
                truncate("All " + subscribers.size() + " push send(s) failed. statuses="
                        + String.join(",", statuses) + ". last=" + lastFailure, 512));
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
        // sendRaw используется кампаниями, где отправка идёт по одному устройству.
        // Логируем те же безопасные идентификаторы, что и в broadcast: token/user/host/status.
        log.info("webpush sendRaw started: deviceTokenId={} userId={} endpointHost={} targetUrl={} bodyLength={}",
                token.getId(), userId(token), endpointHost(token), targetUrl, body == null ? 0 : body.length());
        Subscription sub = parseSubscription(token.getSubscriptionJson());
        if (sub == null) {
            log.warn("webpush sendRaw invalid: deviceTokenId={} userId={} endpointHost={} reason=invalid_subscription_json",
                    token.getId(), userId(token), endpointHost(token));
            return PushDeliveryResult.failed("webpush", "invalid_subscription", "Cannot parse subscription JSON");
        }
        String payload = buildRawPayload(body, targetUrl);
        try {
            // См. комментарий в send(): без явного AES128GCM библиотека использует старый AESGCM
            // и FCM отклоняет запрос из-за формата Crypto-Key header.
            HttpResponse response = pushService.send(new Notification(sub, payload), Encoding.AES128GCM);
            int statusCode = statusCode(response);
            if (isSuccessfulPushStatus(statusCode)) {
                log.info("webpush sendRaw accepted: deviceTokenId={} userId={} endpointHost={} httpStatus={}",
                        token.getId(), userId(token), endpointHost(token), statusCode);
                return PushDeliveryResult.sent("webpush", "token-" + token.getId() + " status=" + statusCode);
            }
            String responseDetails = responseDetails(response);
            log.warn("webpush sendRaw rejected: deviceTokenId={} userId={} endpointHost={} httpStatus={} response={}",
                    token.getId(), userId(token), endpointHost(token), statusCode, responseDetails);
            return PushDeliveryResult.failed("webpush", "http_" + statusCode,
                    truncate("Push service returned HTTP " + statusCode + " " + responseDetails, 512));
        } catch (Exception e) {
            log.warn("webpush sendRaw exception: deviceTokenId={} userId={} endpointHost={} exception={} message={}",
                    token.getId(), userId(token), endpointHost(token), e.getClass().getSimpleName(), e.getMessage());
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

    /**
     * Возвращает id владельца подписки для корреляции с app_users.
     * Email/имя в push-логах не нужны: id достаточно для диагностики и безопаснее для логов.
     */
    private Long userId(DeviceToken token) {
        return token.getUser() == null ? null : token.getUser().getId();
    }

    /**
     * Достаёт только host из endpoint-а подписки: например fcm.googleapis.com или web.push.apple.com.
     * Полный endpoint нельзя писать в application logs, потому что он фактически является адресом
     * конкретной browser push-подписки и может жить долго.
     */
    private String endpointHost(DeviceToken token) {
        String json = token.getSubscriptionJson();
        if (json == null || json.isBlank()) {
            return "missing";
        }
        try {
            String endpoint = objectMapper.readTree(json).path("endpoint").asText();
            if (endpoint == null || endpoint.isBlank()) {
                return "missing";
            }
            String host = URI.create(endpoint).getHost();
            return host == null || host.isBlank() ? "unknown" : host;
        } catch (Exception e) {
            return "invalid";
        }
    }

    /**
     * Внешний Web Push endpoint считается принявшим сообщение только при HTTP 2xx.
     * 404/410 обычно означают устаревшую подписку, 403 часто указывает на проблему VAPID-ключей.
     */
    private boolean isSuccessfulPushStatus(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Защищаемся от неожиданного null-ответа библиотеки: в логах это будет status=-1,
     * а отправка не будет ошибочно засчитана как успешная.
     */
    private int statusCode(HttpResponse response) {
        if (response == null || response.getStatusLine() == null) {
            return -1;
        }
        return response.getStatusLine().getStatusCode();
    }

    /**
     * Для неуспешных ответов читаем короткую причину от push-сервиса.
     * У FCM при 403 в body часто лежит самое полезное: например, что VAPID JWT не принят,
     * endpoint не соответствует ключу или подписка больше невалидна.
     *
     * Заголовки намеренно не логируем: там могут оказаться служебные токены авторизации.
     */
    private String responseDetails(HttpResponse response) {
        if (response == null) {
            return "response=null";
        }

        String reason = response.getStatusLine() == null ? "" : response.getStatusLine().getReasonPhrase();
        String body = "";
        try {
            if (response.getEntity() != null) {
                body = EntityUtils.toString(response.getEntity());
            }
        } catch (Exception e) {
            body = "cannot_read_body:" + e.getClass().getSimpleName();
        }

        return truncate("reason=" + reason + " body=" + body, 300);
    }

    /**
     * Поля provider_message_id/error_message ограничены в БД, поэтому длинные summaries режем заранее.
     * Полная детализация при этом остаётся в application log.
     */
    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
