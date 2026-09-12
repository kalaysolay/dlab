package kz.damulab.ai;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Единая точка выбора AI. Провайдер и модель для каждого сценария читаются из
 * {@code ai_runtime_settings}; конфигурационный {@code real-providers-enabled}
 * остаётся аварийным server-side выключателем внешних запросов.
 */
@Primary
@Component
public class AiProviderRouter implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(AiProviderRouter.class);

    private final AiProviderProperties properties;
    private final AiRuntimeSettingsService settings;
    private final StubAiProvider stub;
    private final OpenAiProvider openAi;
    private final DeepSeekProvider deepSeek;

    public AiProviderRouter(
            AiProviderProperties properties,
            AiRuntimeSettingsService settings,
            StubAiProvider stub,
            OpenAiProvider openAi,
            DeepSeekProvider deepSeek
    ) {
        this.properties = properties;
        this.settings = settings;
        this.stub = stub;
        this.openAi = openAi;
        this.deepSeek = deepSeek;
    }

    /** Логирует только наличие секретов, но никогда не их значения. */
    @PostConstruct
    void logStartupAiBinding() {
        boolean openAiKey = isConfigured(properties.getOpenai().getApiKey());
        boolean deepSeekKey = isConfigured(properties.getDeepseek().getApiKey());
        log.info(
                "damulab.ai binding: runtime routes come from DB; realProvidersEnabled={} "
                        + "openaiApiKeyConfigured={} deepseekApiKeyConfigured={}",
                properties.isRealProvidersEnabled(),
                openAiKey,
                deepSeekKey
        );
    }

    /** Вызывает маршрут QUESTIONS, актуальный на момент начала генерации. */
    @Override
    public AiQuestionGenerationResult generateQuestions(AiQuestionGenerationRequest request) {
        AiRuntimeSelection selection = settings.resolve(AiUsageType.QUESTIONS);
        log.info(
                "AiProviderRouter: generateQuestions provider={} model={} (prompt=questionGenerationPrompt)",
                selection.provider(),
                selection.model()
        );
        if (selection.provider() == AiProviderCode.STUB) {
            return stub.generateQuestions(request);
        }
        ensureExternalProvidersEnabled(selection.provider());
        return switch (selection.provider()) {
            case OPENAI -> openAi.generateQuestions(request, selection.model());
            case DEEPSEEK -> deepSeek.generateQuestions(request, selection.model());
            case STUB -> throw new IllegalStateException("Stub route must be handled before external dispatch");
        };
    }

    /** Вызывает отдельный маршрут LECTURES для генерации мини-лекции к вопросу. */
    @Override
    public AiMiniLectureResult generateMiniLecture(MiniLectureGenerationRequest request) {
        AiRuntimeSelection selection = settings.resolve(AiUsageType.LECTURES);
        log.info(
                "AiProviderRouter: generateMiniLecture provider={} model={} "
                        + "(prompt=miniLecturePrompt + quality validator)",
                selection.provider(),
                selection.model()
        );
        if (selection.provider() == AiProviderCode.STUB) {
            log.warn("Мини-лекция: в настройках админки выбран stub — внешний LLM не вызывается");
            return stub.generateMiniLecture(request);
        }
        ensureExternalProvidersEnabled(selection.provider());
        return switch (selection.provider()) {
            case OPENAI -> openAi.generateMiniLecture(request, selection.model());
            case DEEPSEEK -> deepSeek.generateMiniLecture(request, selection.model());
            case STUB -> throw new IllegalStateException("Stub route must be handled before external dispatch");
        };
    }

    /** Вызывает независимый маршрут TRANSLATIONS для пользовательского перевода. */
    @Override
    public AiTextResult translate(AiTranslationRequest request) {
        AiRuntimeSelection selection = translationSelection("translate");
        if (selection.provider() == AiProviderCode.STUB) {
            return stub.translate(request);
        }
        ensureExternalProvidersEnabled(selection.provider());
        return switch (selection.provider()) {
            case OPENAI -> openAi.translate(request, selection.model());
            case DEEPSEEK -> deepSeek.translate(request, selection.model());
            case STUB -> throw new IllegalStateException("Stub route must be handled before external dispatch");
        };
    }

    /** Использует тот же маршрут и модель, чтобы разбор соответствовал выполненному переводу. */
    @Override
    public AiTextResult explainTranslation(AiTranslationExplanationRequest request) {
        AiRuntimeSelection selection = translationSelection("explainTranslation");
        if (selection.provider() == AiProviderCode.STUB) {
            return stub.explainTranslation(request);
        }
        ensureExternalProvidersEnabled(selection.provider());
        return switch (selection.provider()) {
            case OPENAI -> openAi.explainTranslation(request, selection.model());
            case DEEPSEEK -> deepSeek.explainTranslation(request, selection.model());
            case STUB -> throw new IllegalStateException("Stub route must be handled before external dispatch");
        };
    }

    private AiRuntimeSelection translationSelection(String operation) {
        AiRuntimeSelection selection = settings.resolve(AiUsageType.TRANSLATIONS);
        // Пользовательский текст намеренно не логируется: он может содержать персональные данные.
        log.info("AiProviderRouter: {} provider={} model={}", operation, selection.provider(), selection.model());
        return selection;
    }

    private void ensureExternalProvidersEnabled(AiProviderCode provider) {
        if (properties.isRealProvidersEnabled()) {
            return;
        }
        log.error(
                "AI request blocked: real providers are disabled by damulab.ai.real-providers-enabled; provider={}",
                provider
        );
        throw new AiProviderException("ai_provider_disabled", "Real AI providers are disabled by configuration");
    }

    private boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }
}
