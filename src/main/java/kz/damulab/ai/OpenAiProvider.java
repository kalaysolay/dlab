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
public class OpenAiProvider extends ExternalAiProviderSupport implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);
    private static final String OP_QUESTIONS = "openai_question_drafts";
    private static final String OP_MINI_LECTURE = "openai_mini_lecture";
    private static final String ENDPOINT = "/v1/responses";

    private final AiProviderProperties properties;
    private final AiPromptBuilder promptBuilder;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public OpenAiProvider(
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
        AiProviderProperties.Provider openai = properties.getOpenai();
        requireConfigured(openai.getApiKey(), "openai_api_key_missing");
        String model = openai.getModel();
        String systemPrompt = promptBuilder.systemPrompt();
        String userPrompt = promptBuilder.questionGenerationPrompt(request);
        AiCallLogger.logOutbound(
                log,
                OP_QUESTIONS,
                "openai",
                model,
                "damulab.ai.openai.model / OPENAI_MODEL",
                ENDPOINT,
                1,
                1,
                systemPrompt,
                userPrompt
        );
        Map<String, Object> body = Map.of(
                "model", model,
                "input", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "text", Map.of("format", Map.of(
                        "type", "json_schema",
                        "name", "damulab_ai_question_batch",
                        "strict", true,
                        "schema", questionJsonSchema()
                ))
        );
        try {
            JsonNode response = post(openai, body);
            String outputJson = extractOpenAiText(response == null ? objectMapper.createObjectNode() : response);
            AiCallLogger.logInboundRaw(log, OP_QUESTIONS, model, 1, outputJson);
            List<AiGeneratedQuestionDraft> drafts = parseDrafts(outputJson);
            AiCallLogger.logQuestionDrafts(log, OP_QUESTIONS, model, drafts);
            return new AiQuestionGenerationResult("openai", model, drafts);
        } catch (RestClientException ex) {
            log.error("OpenAI question_drafts: HTTP/сеть — {}", ex.getMessage(), ex);
            throw new AiProviderException("openai_request_failed", ex.getMessage());
        }
    }

    @Override
    public AiMiniLectureResult generateMiniLecture(MiniLectureGenerationRequest request) {
        AiProviderProperties.Provider openai = properties.getOpenai();
        requireConfigured(openai.getApiKey(), "openai_api_key_missing");
        String model = properties.resolvedMiniLectureOpenAiModel();
        String modelConfigKey = properties.getMiniLecture().getOpenaiModel() == null
                || properties.getMiniLecture().getOpenaiModel().isBlank()
                ? "damulab.ai.openai.model / OPENAI_MODEL (fallback)"
                : "damulab.ai.mini-lecture.openai-model / OPENAI_MINI_LECTURE_MODEL";
        String systemPrompt = promptBuilder.miniLectureSystemPrompt();
        String userPrompt = promptBuilder.miniLecturePrompt(request);
        AiProviderException lastQualityError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            String attemptPrompt = attempt == 1
                    ? userPrompt
                    : userPrompt + MiniLectureQualityValidator.RETRY_SUFFIX;
            if (attempt > 1) {
                log.warn(
                        "OpenAI mini_lecture: повтор {}/3 после отклонения качества: {}",
                        attempt,
                        lastQualityError.getMessage()
                );
            }
            AiCallLogger.logOutbound(
                    log,
                    OP_MINI_LECTURE,
                    "openai",
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
                        "input", List.of(
                                Map.of("role", "system", "content", systemPrompt),
                                Map.of("role", "user", "content", attemptPrompt)
                        ),
                        "text", Map.of("format", Map.of(
                                "type", "json_schema",
                                "name", "damulab_mini_lecture_structured",
                                "strict", true,
                                "schema", miniLectureStructuredJsonSchema()
                        ))
                );
                JsonNode response = post(openai, body);
                String outputJson = extractOpenAiText(response == null ? objectMapper.createObjectNode() : response);
                AiMiniLectureResult result = finalizeMiniLecture(outputJson, request, OP_MINI_LECTURE, model, attempt);
                log.info(
                        "OpenAI mini_lecture: успех attempt={}/3 HTML ru={} kk={}",
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
                        "OpenAI mini_lecture: ошибка code={} message={}",
                        ex.getCode(),
                        ex.getMessage()
                );
                throw ex;
            } catch (RestClientException ex) {
                log.error("OpenAI mini_lecture: HTTP/сеть — {}", ex.getMessage(), ex);
                throw new AiProviderException("openai_request_failed", ex.getMessage());
            }
        }
        throw lastQualityError == null
                ? new AiProviderException("ai_mini_lecture_too_brief", "Mini-lecture quality check failed")
                : lastQualityError;
    }

    private JsonNode post(AiProviderProperties.Provider openai, Map<String, Object> body) {
        return restClientBuilder
                .baseUrl(openai.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + openai.getApiKey())
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
