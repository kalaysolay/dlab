package kz.damulab.parentreports;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Настройки Telegram из окружения; без полного набора секретов отправка выключена. */
@Component
public record TelegramProperties(
        @Value("${damulab.telegram.enabled:false}") boolean enabled,
        @Value("${damulab.telegram.bot-token:}") String botToken,
        @Value("${damulab.telegram.bot-username:}") String botUsername,
        @Value("${damulab.telegram.webhook-secret:}") String webhookSecret,
        @Value("${damulab.telegram.mode:webhook}") String mode,
        @Value("${damulab.email-verification.public-base-url:http://localhost:8080}") String publicBaseUrl) {
    public boolean available() {
        return enabled && !botToken.isBlank() && botUsername.matches("[A-Za-z0-9_]+")
                && ("polling".equals(mode) || ("webhook".equals(mode) && !webhookSecret.isBlank()));
    }
    public boolean polling() { return "polling".equals(mode); }
}
