package kz.damulab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google OAuth client credentials из {@code damulab.auth.google.*}.
 * Секрет используется только сервером и должен приходить из env, а не из репозитория.
 */
@ConfigurationProperties(prefix = "damulab.auth.google")
public class GoogleOAuthProperties {

    private boolean enabled;
    private String clientId;
    private String clientSecret;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
}
