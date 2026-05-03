package kz.damulab.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "damulab.ai")
public class AiProviderProperties {

    private String provider = "stub";
    private String fallbackProvider = "deepseek";
    private boolean realProvidersEnabled;
    private final Provider openai = new Provider("https://api.openai.com", "gpt-5.2", null);
    private final Provider deepseek = new Provider("https://api.deepseek.com", "deepseek-chat", null);
    private MiniLecture miniLecture = new MiniLecture();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getFallbackProvider() {
        return fallbackProvider;
    }

    public void setFallbackProvider(String fallbackProvider) {
        this.fallbackProvider = fallbackProvider;
    }

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

    public MiniLecture getMiniLecture() {
        return miniLecture;
    }

    public void setMiniLecture(MiniLecture miniLecture) {
        this.miniLecture = miniLecture == null ? new MiniLecture() : miniLecture;
    }

    public static class MiniLecture {

        private String openaiModel = "gpt-4o";
        private String deepseekModel = "deepseek-chat";

        public String getOpenaiModel() {
            return openaiModel;
        }

        public void setOpenaiModel(String openaiModel) {
            this.openaiModel = openaiModel;
        }

        public String getDeepseekModel() {
            return deepseekModel;
        }

        public void setDeepseekModel(String deepseekModel) {
            this.deepseekModel = deepseekModel;
        }
    }

    public static class Provider {

        private String baseUrl;
        private String model;
        private String apiKey;

        public Provider() {
        }

        public Provider(String baseUrl, String model, String apiKey) {
            this.baseUrl = baseUrl;
            this.model = model;
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
