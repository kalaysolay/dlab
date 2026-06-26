package kz.damulab.notifications;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO входящего запроса POST /api/push/subscribe.
 *
 * Соответствует формату PushSubscription.toJSON() из браузерного Web Push API:
 * { "endpoint": "https://...", "keys": { "p256dh": "...", "auth": "..." } }
 *
 * endpoint — URL push-сервиса (Firefox: Mozilla, Chrome: Google); уникален per-browser-profile.
 * keys.p256dh — EC Diffie-Hellman public key клиента (Base64url) для шифрования payload.
 * keys.auth  — случайный 16-байтовый секрет (Base64url) для аутентификации.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PushSubscriptionRequest(

        @NotBlank
        String endpoint,

        @NotNull
        @Valid
        Keys keys,

        /**
         * Тайм-зона устройства в формате IANA (например "Asia/Almaty").
         * Необязательное поле; передаётся pwa.js через Intl.DateTimeFormat().resolvedOptions().timeZone.
         */
        String timezone

) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Keys(

            @NotBlank
            String p256dh,

            @NotBlank
            String auth
    ) {
    }
}
