package kz.damulab.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Выбор реализации PushProvider в зависимости от конфигурации VAPID.
 *
 * WebPushProvider — активируется при заданных VAPID-ключах (damulab.push.vapid-public-key).
 * StubPushProvider — активируется по умолчанию (local-профиль, тесты, отсутствие VAPID).
 *
 * Логика выбора: если VapidProperties.isConfigured() == true → WebPushProvider,
 * иначе Spring @ConditionalOnMissingBean подхватывает StubPushProvider.
 */
@Configuration
public class PushProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(PushProviderConfig.class);

    /**
     * Реальный провайдер Web Push через RFC 8030 + VAPID.
     * Создаётся только если VAPID_PUBLIC_KEY и VAPID_PRIVATE_KEY заданы и непусты.
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
            "'${damulab.push.vapid-public-key:}'.length() > 0 " +
            "and '${damulab.push.vapid-private-key:}'.length() > 0"
    )
    PushProvider webPushProvider(VapidProperties vapid,
                                 DeviceTokenRepository deviceTokens,
                                 ObjectMapper objectMapper) throws Exception {
        log.info("PushProvider: Web Push (VAPID) активирован — реальная доставка в браузер");
        return new WebPushProvider(vapid, deviceTokens, objectMapper);
    }

    /**
     * Stub-провайдер: логирует отправку, не делает реальных HTTP-запросов.
     * Активен в local-профиле и тестах (VAPID-ключи не заданы).
     */
    @Bean
    @ConditionalOnMissingBean(PushProvider.class)
    PushProvider stubPushProvider() {
        log.info("PushProvider: Stub — реальные Web Push уведомления отключены (VAPID не задан)");
        return new StubPushProvider();
    }
}
