package kz.damulab.notifications;

/**
 * Абстракция провайдера push-уведомлений.
 *
 * send() — для разовых push из outbox (PushNotification entity).
 * sendRaw() — для кампаний: текст уже подставлен, устройство передаётся явно.
 *
 * Реализации: WebPushProvider (реальный RFC 8030 VAPID), StubPushProvider (тесты/local).
 */
public interface PushProvider {

    /** Отправляет разовое уведомление из outbox всем активным подписчикам. */
    PushDeliveryResult send(PushNotification notification);

    /**
     * Отправляет push конкретному устройству с готовым текстом и URL.
     * Используется кампаниями, которые отправляют персонализированный текст per-device.
     *
     * @param body      текст уведомления (переменные уже подставлены)
     * @param targetUrl URL для перехода при клике (относительный, например "/student/quiz-hub")
     * @param token     подписка конкретного устройства
     */
    PushDeliveryResult sendRaw(String body, String targetUrl, DeviceToken token);
}
