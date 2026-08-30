package kz.damulab.auth;

import kz.damulab.users.AppUser;

/** Порт отправки писем, отделяющий доменную логику подтверждения от SMTP.BZ. */
public interface VerificationEmailSender {

    /**
     * Отправляет пользователю ссылку с одноразовым токеном.
     *
     * @return нормализованные поля ответа провайдера
     * @throws EmailDeliveryException при ошибке HTTP, сети, конфигурации или формата ответа
     */
    EmailSendResult sendVerificationEmail(AppUser user, String rawToken);
}
