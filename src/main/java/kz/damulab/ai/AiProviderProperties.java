package kz.damulab.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "damulab.ai")
public class AiProviderProperties {

    private boolean realProvidersEnabled;
    private final Provider openai = new Provider("https://api.openai.com", null);
    private final Provider deepseek = new Provider("https://api.deepseek.com", null);

    public boolean isRealProvidersEnabled() {
        return realProvidersEnabled;
    }

    public void setRealProvidersEnabled(boolean realProvidersEnabled) {
        this.realProvidersEnabled = realProvidersEnabled;
    }

    public Provider getOpenai() {
        return openai;
    }

    public Provider getDeepseek() {
        return deepseek;
    }

    /** Серверные секреты и endpoint провайдера; модель выбирается в админке и хранится в БД. */
    public static class Provider {

        private String baseUrl;
        private String apiKey;

        public Provider() {
        }

        public Provider(String baseUrl, String apiKey) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
