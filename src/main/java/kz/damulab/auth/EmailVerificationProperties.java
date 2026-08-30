package kz.damulab.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки подтверждения email и HTTP API SMTP.BZ.
 *
 * <p>Поведение управляется ключами {@code damulab.email-verification.*}. При выключенном
 * {@code enabled} регистрация остаётся синхронной и сразу активирует пользователя — это удобно
 * для локальной разработки. В production секрет API передаётся только через окружение.</p>
 */
@Component
@ConfigurationProperties(prefix = "damulab.email-verification")
public class EmailVerificationProperties {

    private boolean enabled;
    private String publicBaseUrl = "http://localhost:8080";
    private Duration tokenTtl = Duration.ofHours(24);
    private Duration resendCooldown = Duration.ofMinutes(1);
    private String apiBaseUrl = "https://api.smtp.bz/v1";
    private String apiKey;
    private String from;
    private String senderName = "Damulab";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public Duration getTokenTtl() {
        return tokenTtl;
    }

    public void setTokenTtl(Duration tokenTtl) {
        this.tokenTtl = tokenTtl;
    }

    public Duration getResendCooldown() {
        return resendCooldown;
    }

    public void setResendCooldown(Duration resendCooldown) {
        this.resendCooldown = resendCooldown;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }
}
