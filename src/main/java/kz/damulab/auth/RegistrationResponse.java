package kz.damulab.auth;

import java.util.Set;

/** Ответ регистрации: прежние данные пользователя плюс состояние обязательной email-проверки. */
public record RegistrationResponse(
        Long id,
        String email,
        String fullName,
        Set<String> roles,
        boolean verificationRequired,
        String emailDeliveryStatus
) {
}
