package kz.damulab.ai;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class DeepSeekProvider extends ExternalAiProviderSupport {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekProvider.class);
    private static final String OP_QUESTIONS = "deepseek_question_drafts";
    private static final String OP_MINI_LECTURE = "deepseek_mini_lecture";
    private static final String OP_LECTURE = "deepseek_lecture";
    private static final String ENDPOINT = "/chat/completions";
    private static final int TRANSLATION_MAX_OUTPUT_TOKENS = 4096;

    private final AiProviderProperties properties;
    private final AiPromptBuilder promptBuilder;
    private final AiTranslationPromptService translationPrompts;
    private final AiLecturePromptService lecturePrompts;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Autowired
    public DeepSeekProvider(
            AiProviderProperties properties,
            AiPromptBuilder promptBuilder,
            AiTranslationPromptService translationPrompts,
            AiLecturePromptService lecturePrompts,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            AiDraftSchemaValidator validator
    ) {
        super(objectMapper, validator);
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.translationPrompts = translationPrompts;
        this.lecturePrompts = lecturePrompts;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    /** Совместимый конструктор для изолированных тестов переводчика, где промпт лекций не используется. */
    DeepSeekProvider(
            AiProviderProperties properties,
            AiPromptBuilder promptBuilder,
            AiTranslationPromptService translationPrompts,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            AiDraftSchemaValidator validator
    ) {
        this(properties, promptBuilder, translationPrompts, null, restClientBuilder, objectMapper, validator);
    }

    /**
     * Генерация черновиков через DeepSeek Chat Completions.
     * В отличие от OpenAI, здесь нет strict json_schema — контракт держим промптом
     * ({@link #questionSchemaPromptAppendix}) и мягким разбором ({@link #parseDrafts(String, kz.damulab.questions.QuestionType)}).
     */
    public AiQuestionGenerationResult generateQuestions(AiQuestionGenerationRequest request, String model) {
        AiProviderProperties.Provider deepseek = properties.getDeepseek();
        requireConfigured(deepseek.getApiKey(), "deepseek_api_key_missing");
        String systemPrompt = promptBuilder.systemPrompt();
        // Явная JSON Schema в user-промпте: иначе deepseek-chat часто пропускает questionType
        // или отдаёт snake_case → ai_schema_invalid на валидации.
        String userPrompt = promptBuilder.questionGenerationPrompt(request)
                + questionSchemaPromptAppendix(request.questionType());
        AiCallLogger.logOutbound(
                log,
                OP_QUESTIONS,
                "deepseek",
                model,
                "admin.ai_runtime_settings[QUESTIONS]",
                ENDPOINT,
                1,
                1,
                systemPrompt,
                userPrompt
        );
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.2
        );
        try {
            JsonNode response = post(deepseek, body);
            String outputJson = extractDeepSeekText(response == null ? objectMapper.createObjectNode() : response);
            AiCallLogger.logInboundRaw(log, OP_QUESTIONS, model, 1, outputJson);
            List<AiGeneratedQuestionDraft> drafts = parseDrafts(outputJson, request.questionType());
            AiCallLogger.logQuestionDrafts(log, OP_QUESTIONS, model, drafts);
            return new AiQuestionGenerationResult("deepseek", model, drafts);
        } catch (RestClientException ex) {
            log.error("DeepSeek question_drafts: HTTP/сеть — {}", ex.getMessage(), ex);
            throw new AiProviderException("deepseek_request_failed", ex.getMessage());
        }
    }

    public AiMiniLectureResult generateMiniLecture(MiniLectureGenerationRequest request, String model) {
        AiProviderProperties.Provider deepseek = properties.getDeepseek();
        requireConfigured(deepseek.getApiKey(), "deepseek_api_key_missing");
        String systemPrompt = promptBuilder.miniLectureSystemPrompt();
        String jsonSuffix = """

                Return JSON only (no markdown). Root object must have keys "ru" and "kz".
                Each value is an object with string fields: title, theory, question_analysis, common_mistake, example_analysis, summary.
                """;
        String userPrompt = promptBuilder.miniLecturePrompt(request) + jsonSuffix;
        AiProviderException lastQualityError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            String attemptPrompt = attempt == 1
                    ? userPrompt
                    : userPrompt + MiniLectureQualityValidator.RETRY_SUFFIX;
            if (attempt > 1) {
                log.warn(
                        "DeepSeek mini_lecture: повтор {}/3 после отклонения качества: {}",
                        attempt,
                        lastQualityError.getMessage()
                );
            }
            AiCallLogger.logOutbound(
                    log,
                    OP_MINI_LECTURE,
                    "deepseek",
                    model,
                    "admin.ai_runtime_settings[LECTURES]",
                    ENDPOINT,
                    attempt,
                    3,
                    systemPrompt,
                    attemptPrompt
            );
            try {
                Map<String, Object> body = Map.of(
                        "model", model,
                        "messages", List.of(
                                Map.of("role", "system", "content", systemPrompt),
                                Map.of("role", "user", "content", attemptPrompt)
                        ),
                        "response_format", Map.of("type", "json_object"),
                        "temperature", 0.25
                );
                JsonNode response = post(deepseek, body);
                String outputJson = extractDeepSeekText(response == null ? objectMapper.createObjectNode() : response);
                AiMiniLectureResult result = finalizeMiniLecture(outputJson, request, OP_MINI_LECTURE, model, attempt);
                log.info(
                        "DeepSeek mini_lecture: успех attempt={}/3 HTML ru={} kk={}",
                        attempt,
                        result.contentRu().length(),
                        result.contentKk().length()
                );
                return result;
            } catch (AiProviderException ex) {
                if (isMiniLectureQualityFailure(ex) && attempt < 3) {
                    lastQualityError = ex;
                    continue;
                }
                log.error(
                        "DeepSeek mini_lecture: ошибка code={} message={}",
                        ex.getCode(),
                        ex.getMessage()
                );
                throw ex;
            } catch (RestClientException ex) {
                log.error("DeepSeek mini_lecture: HTTP/сеть — {}", ex.getMessage(), ex);
                throw new AiProviderException("deepseek_request_failed", ex.getMessage());
            }
        }
        throw lastQualityError == null
                ? new AiProviderException("ai_mini_lecture_too_brief", "Mini-lecture quality check failed")
                : lastQualityError;
    }

    /** DeepSeek получает ту же JSON-схему текстом и проходит тот же серверный порог 95/100. */
    public AiLectureGenerationResult generateLecture(AiLectureGenerationRequest request, String model) {
        AiProviderProperties.Provider deepseek = properties.getDeepseek();
        requireConfigured(deepseek.getApiKey(), "deepseek_api_key_missing");
        AiRenderedPrompt prompt = lecturePrompts.render(request);
        String baseUserPrompt = prompt.userPrompt() + lectureSchemaPromptAppendix();
        AiLectureQualityException lastQualityError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            String userPrompt = attempt == 1
                    ? baseUserPrompt
                    : baseUserPrompt + AiLectureQualityValidator.retrySuffix(lastQualityError.getReport());
            AiCallLogger.logOutbound(
                    log, OP_LECTURE, "deepseek", model, "ai_prompts[LECTURE_GENERATE]",
                    ENDPOINT, attempt, 3, prompt.systemPrompt(), userPrompt
            );
            try {
                Map<String, Object> body = Map.of(
                        "model", model,
                        "messages", List.of(
                                Map.of("role", "system", "content", prompt.systemPrompt()),
                                Map.of("role", "user", "content", userPrompt)
                        ),
                        "response_format", Map.of("type", "json_object"),
                        "temperature", 0.2,
                        "max_tokens", 12000
                );
                JsonNode response = post(deepseek, body);
                String outputJson = extractDeepSeekText(response == null ? objectMapper.createObjectNode() : response);
                return finalizeLecture(outputJson, request, "deepseek", model, OP_LECTURE, attempt);
            } catch (AiLectureQualityException ex) {
                lastQualityError = ex;
                log.warn("DeepSeek lecture: качество {}/100, attempt={}/3", ex.getReport().score(), attempt, 3);
                if (attempt == 3) {
                    throw ex;
                }
            } catch (RestClientException ex) {
                log.error("DeepSeek lecture: HTTP/сеть — {}", ex.getMessage(), ex);
                throw new AiProviderException("deepseek_request_failed", ex.getMessage());
            }
        }
        throw lastQualityError;
    }

    /** Перевод через Chat Completions с актуальным промптом из БД. */
    public AiTextResult translate(AiTranslationRequest request, String model) {
        AiRenderedPrompt prompt = translationPrompts.renderTranslation(request);
        return generateText(
                "deepseek_translation",
                prompt.systemPrompt(),
                prompt.userPrompt(),
                model,
                TRANSLATION_MAX_OUTPUT_TOKENS
        );
    }

    /** Просит модель разобрать перевод по актуальному промпту из БД. */
    public AiTextResult explainTranslation(AiTranslationExplanationRequest request, String model) {
        AiRenderedPrompt prompt = translationPrompts.renderExplanation(request);
        return generateText(
                "deepseek_translation_explanation",
                prompt.systemPrompt(),
                prompt.userPrompt(),
                model,
                request.explanationMode().maxOutputTokens()
        );
    }

    private AiTextResult generateText(
            String operation,
            String systemPrompt,
            String userPrompt,
            String model,
            int maxOutputTokens
    ) {
        AiProviderProperties.Provider deepseek = properties.getDeepseek();
        requireConfigured(deepseek.getApiKey(), "deepseek_api_key_missing");
        // Не используем подробный AI-логгер: перевод может содержать личный текст ученика.
        log.info("AI >>> op={} provider=deepseek model={} inputLen={}", operation, model, userPrompt.length());
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "thinking", Map.of("type", "disabled"),
                "temperature", 0.1,
                "max_tokens", maxOutputTokens
        );
        try {
            JsonNode response = post(deepseek, body);
            String text = extractDeepSeekText(response == null ? objectMapper.createObjectNode() : response).trim();
            if (text.isBlank()) {
                throw new AiProviderException("deepseek_response_empty", "DeepSeek returned an empty text");
            }
            log.info("AI <<< op={} provider=deepseek model={} outputLen={}", operation, model, text.length());
            return new AiTextResult("deepseek", model, text);
        } catch (RestClientException ex) {
            log.error("DeepSeek {}: HTTP/сеть — {}", operation, ex.getMessage(), ex);
            throw new AiProviderException("deepseek_request_failed", ex.getMessage());
        }
    }

    private JsonNode post(AiProviderProperties.Provider deepseek, Map<String, Object> body) {
        return restClientBuilder
                .baseUrl(deepseek.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + deepseek.getApiKey())
                .build()
                .post()
                .uri(ENDPOINT)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private void requireConfigured(String value, String code) {
        if (value == null || value.isBlank()) {
            throw new AiProviderException(code, code);
        }
    }
}
