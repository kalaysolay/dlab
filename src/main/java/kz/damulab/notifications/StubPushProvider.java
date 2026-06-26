package kz.damulab.notifications;

/**
 * Stub-реализация PushProvider для local-профиля и тестов.
 * Не делает реальных HTTP-запросов; имитирует успешную (или намеренно неудачную) отправку.
 * Активируется через PushProviderConfig при отсутствии VAPID-ключей.
 */
public class StubPushProvider implements PushProvider {

    static final String FAILURE_TOKEN = "__FAIL_PUSH__";

    @Override
    public PushDeliveryResult send(PushNotification notification) {
        if (notification.getText().contains(FAILURE_TOKEN)) {
            return PushDeliveryResult.failed("stub", "stub_push_failure", "Stub push failure requested");
        }
        return PushDeliveryResult.sent("stub", "stub-push-" + notification.getId());
    }

    /**
     * Stub для кампаний: успешен, если body не содержит FAILURE_TOKEN.
     * В тестах можно подставить токен через body_template, чтобы проверить ветку ошибки.
     */
    @Override
    public PushDeliveryResult sendRaw(String body, String targetUrl, DeviceToken token) {
        if (body != null && body.contains(FAILURE_TOKEN)) {
            return PushDeliveryResult.failed("stub", "stub_push_failure", "Stub push failure requested");
        }
        return PushDeliveryResult.sent("stub", "stub-campaign-token-" + token.getId());
    }
}
