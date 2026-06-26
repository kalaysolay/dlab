package kz.damulab.ai;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Единая точка выбора AI: {@code damulab.ai.provider}, флаг {@code real-providers-enabled}, fallback.
 * Логирует ветвление по мини-лекции и генерации черновиков — смотреть консоль / файл логов при отладке.
 */
@Primary
@Component
public class AiProviderRouter implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(AiProviderRouter.class);

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

    /**
     * Одна строка при старте (ASCII): в Windows-консоли часто ломается UTF-8 в логах, а эти поля критичны для отладки AI.
     */
    @PostConstruct
    void logStartupAiBinding() {
        String provider = properties.getProvider() == null ? "" : properties.getProvider().trim();
        boolean openAiKey = properties.getOpenai().getApiKey() != null
                && !properties.getOpenai().getApiKey().isBlank();
        log.info(
                "damulab.ai binding: provider={} realProvidersEnabled={} openaiApiKeyConfigured={} "
                        + "openaiModel={} miniLectureOpenAiModel={} deepseekModel={} miniLectureDeepSeekModel={}",
                provider.isEmpty() ? "(empty)" : provider,
                properties.isRealProvidersEnabled(),
                openAiKey,
                properties.getOpenai().getModel(),
                properties.resolvedMiniLectureOpenAiModel(),
                properties.getDeepseek().getModel(),
                properties.resolvedMiniLectureDeepSeekModel()
        );
    }

    @Override
    public AiQuestionGenerationResult generateQuestions(AiQuestionGenerationRequest request) {
        String provider = normalize(properties.getProvider());
        log.info(
                "AiProviderRouter: generateQuestions provider='{}' (prompt=questionGenerationPrompt, поле explanationRu — НЕ мини-лекция)",
                provider.isEmpty() ? "(empty)" : provider
        );
        if ("stub".equals(provider)) {
            return stub.generateQuestions(request);
        }
        if (!properties.isRealProvidersEnabled()) {
            throw new AiProviderException("ai_provider_disabled", "Real AI providers are disabled by configuration");
        }
        try {
            return delegate(provider).generateQuestions(request);
        } catch (AiProviderException ex) {
            if (!shouldRetryWithFallback(ex)) {
                log.warn("AI generateQuestions: skip fallback (code={}). Fix primary provider or keys.", ex.getCode());
                throw ex;
            }
            String fallback = normalize(properties.getFallbackProvider());
            if (fallback.isBlank() || fallback.equals(provider) || "stub".equals(fallback)) {
                throw ex;
            }
            log.info("AI generateQuestions: primary '{}' failed, trying fallback '{}'", provider, fallback);
            return delegate(fallback).generateQuestions(request);
        }
    }

    @Override
    public AiMiniLectureResult generateMiniLecture(MiniLectureGenerationRequest request) {
        String provider = normalize(properties.getProvider());
        boolean realOn = properties.isRealProvidersEnabled();
        log.info(
                "AiProviderRouter: generateMiniLecture provider='{}' resolvedOpenAiModel='{}' (prompt=miniLecturePrompt + quality validator)",
                provider.isEmpty() ? "(пусто)" : provider,
                properties.resolvedMiniLectureOpenAiModel()
        );
        if ("stub".equals(provider)) {
            log.warn(
                    "Мини-лекция: выбран stub — внешний LLM не вызывается. "
                            + "Для OpenAI: AI_PROVIDER=openai, AI_REAL_PROVIDERS_ENABLED=true, OPENAI_API_KEY=..."
            );
            return stub.generateMiniLecture(request);
        }
        if (!realOn) {
            log.error(
                    "Мини-лекция: real-провайдеры выключены (damulab.ai.real-providers-enabled=false). "
                            + "Запрос к '{}' не будет отправлен наружу.",
                    provider
            );
            throw new AiProviderException("ai_provider_disabled", "Real AI providers are disabled by configuration");
        }
        try {
            log.info("Мини-лекция: вызов основного провайдера '{}'", provider);
            return delegate(provider).generateMiniLecture(request);
        } catch (AiProviderException ex) {
            log.warn(
                    "Мини-лекция: провайдер '{}' вернул ошибку code={} message={}",
                    provider,
                    ex.getCode(),
                    ex.getMessage()
            );
            if (!shouldRetryWithFallback(ex)) {
                log.warn("Mini-lecture: skip fallback (code={}). Not a transient error — fix OpenAI/DeepSeek config.", ex.getCode());
                throw ex;
            }
            String fallback = normalize(properties.getFallbackProvider());
            if (fallback.isBlank() || fallback.equals(provider) || "stub".equals(fallback)) {
                throw ex;
            }
            log.info("Мини-лекция: пробуем fallback-провайдер '{}'", fallback);
            return delegate(fallback).generateMiniLecture(request);
        }
    }

    /**
     * Fallback имеет смысл при сбое сети/ответа модели. Если не настроен ключ или провайдер — второй бэкенд не поможет.
     */
    private static boolean shouldRetryWithFallback(AiProviderException ex) {
        return switch (ex.getCode()) {
            case "openai_api_key_missing",
                    "deepseek_api_key_missing",
                    "ai_provider_disabled",
                    "ai_provider_unknown" -> false;
            default -> true;
        };
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
