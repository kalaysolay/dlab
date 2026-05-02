package kz.damulab.ai;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class AiProviderRouter implements AiProvider {

    private final AiProviderProperties properties;
    private final StubAiProvider stub;
    private final OpenAiProvider openAi;
    private final DeepSeekProvider deepSeek;

    public AiProviderRouter(
            AiProviderProperties properties,
            StubAiProvider stub,
            OpenAiProvider openAi,
            DeepSeekProvider deepSeek
    ) {
        this.properties = properties;
        this.stub = stub;
        this.openAi = openAi;
        this.deepSeek = deepSeek;
    }

    @Override
    public AiQuestionGenerationResult generateQuestions(AiQuestionGenerationRequest request) {
        String provider = normalize(properties.getProvider());
        if ("stub".equals(provider)) {
            return stub.generateQuestions(request);
        }
        if (!properties.isRealProvidersEnabled()) {
            throw new AiProviderException("ai_provider_disabled", "Real AI providers are disabled by configuration");
        }
        try {
            return delegate(provider).generateQuestions(request);
        } catch (AiProviderException ex) {
            String fallback = normalize(properties.getFallbackProvider());
            if (fallback.isBlank() || fallback.equals(provider) || "stub".equals(fallback)) {
                throw ex;
            }
            return delegate(fallback).generateQuestions(request);
        }
    }

    private AiProvider delegate(String provider) {
        return switch (provider) {
            case "openai" -> openAi;
            case "deepseek" -> deepSeek;
            default -> throw new AiProviderException("ai_provider_unknown", "Unknown AI provider: " + provider);
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
