package kz.damulab.ai;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class DeepSeekProvider extends ExternalAiProviderSupport implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekProvider.class);
    private static final String OP_QUESTIONS = "deepseek_question_drafts";
    private static final String OP_MINI_LECTURE = "deepseek_mini_lecture";
    private static final String ENDPOINT = "/chat/completions";

    private final AiProviderProperties properties;
    private final AiPromptBuilder promptBuilder;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public DeepSeekProvider(
            AiProviderProperties properties,
            AiPromptBuilder promptBuilder,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            AiDraftSchemaValidator validator
    ) {
        super(objectMapper, validator);
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiQuestionGenerationResult generateQuestions(AiQuestionGenerationRequest request) {
        AiProviderProperties.Provider deepseek = properties.getDeepseek();
        requireConfigured(deepseek.getApiKey(), "deepseek_api_key_missing");
        String model = deepseek.getModel();
        String systemPrompt = promptBuilder.systemPrompt();
        String userPrompt = promptBuilder.questionGenerationPrompt(request)
                + "\nReturn JSON only with root object {\"questions\": [...]} and no markdown.";
        AiCallLogger.logOutbound(
                log,
                OP_QUESTIONS,
                "deepseek",
                model,
                "damulab.ai.deepseek.model / DEEPSEEK_MODEL",
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
            List<AiGeneratedQuestionDraft> drafts = parseDrafts(outputJson);
            AiCallLogger.logQuestionDrafts(log, OP_QUESTIONS, model, drafts);
            return new AiQuestionGenerationResult("deepseek", model, drafts);
        } catch (RestClientException ex) {
            log.error("DeepSeek question_drafts: HTTP/сеть — {}", ex.getMessage(), ex);
            throw new AiProviderException("deepseek_request_failed", ex.getMessage());
        }
    }

    @Override
    public AiMiniLectureResult generateMiniLecture(MiniLectureGenerationRequest request) {
        AiProviderProperties.Provider deepseek = properties.getDeepseek();
        requireConfigured(deepseek.getApiKey(), "deepseek_api_key_missing");
        String model = properties.resolvedMiniLectureDeepSeekModel();
        String modelConfigKey = properties.getMiniLecture().getDeepseekModel() == null
                || properties.getMiniLecture().getDeepseekModel().isBlank()
                ? "damulab.ai.deepseek.model / DEEPSEEK_MODEL (fallback)"
                : "damulab.ai.mini-lecture.deepseek-model / DEEPSEEK_MINI_LECTURE_MODEL";
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
                    modelConfigKey,
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
