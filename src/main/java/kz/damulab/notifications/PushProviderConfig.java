package kz.damulab.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
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
        logVapidKeyPairStatus(vapid);
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

    private void logVapidKeyPairStatus(VapidProperties vapid) {
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }

            PublicKey publicKey = Utils.loadPublicKey(vapid.getVapidPublicKey());
            PrivateKey privateKey = Utils.loadPrivateKey(vapid.getVapidPrivateKey());
            boolean valid = Utils.verifyKeyPair(privateKey, publicKey);

            // Логируем только результат проверки и длины значений. Сами ключи не пишем:
            // public key уже виден в HTML, а private key никогда не должен попадать в journal.
            // valid=false почти наверняка объясняет HTTP 403 от FCM/web.push.apple.com.
            log.info("VAPID key pair check: valid={} publicKeyLength={} privateKeyLength={} subjectConfigured={}",
                    valid,
                    vapid.getVapidPublicKey().length(),
                    vapid.getVapidPrivateKey().length(),
                    vapid.getSubject() != null && !vapid.getSubject().isBlank());
        } catch (Exception e) {
            // Не валим приложение целиком: админка и остальной продукт могут работать,
            // но push-доставка будет диагностируемо сломана до исправления VAPID env.
            log.error("VAPID key pair check failed: publicKeyLength={} privateKeyLength={} error={}",
                    vapid.getVapidPublicKey().length(),
                    vapid.getVapidPrivateKey().length(),
                    e.getMessage());
        }
    }
}
