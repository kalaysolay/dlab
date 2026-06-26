package kz.damulab.notifications;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    /**
     * Поиск подписки по провайдеру и хэшу endpoint — для dedup-upsert при регистрации.
     */
    Optional<DeviceToken> findByProviderAndTokenHash(String provider, String tokenHash);

    /**
     * Все активные подписки указанного провайдера — используются WebPushProvider при рассылке.
     */
    List<DeviceToken> findByProviderAndEnabledTrue(String provider);
}
