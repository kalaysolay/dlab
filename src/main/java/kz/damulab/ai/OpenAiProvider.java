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
        Map<String, Object> body = Map.of(
                "model", openai.getModel(),
                "input", List.of(
                        Map.of("role", "system", "content", promptBuilder.systemPrompt()),
                        Map.of("role", "user", "content", promptBuilder.questionGenerationPrompt(request))
                ),
                "text", Map.of("format", Map.of(
                        "type", "json_schema",
                        "name", "damulab_ai_question_batch",
                        "strict", true,
                        "schema", questionJsonSchema()
                ))
        );
        try {
            JsonNode response = restClientBuilder
                    .baseUrl(openai.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + openai.getApiKey())
                    .build()
                    .post()
                    .uri("/v1/responses")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String outputJson = extractOpenAiText(response == null ? objectMapper.createObjectNode() : response);
            return new AiQuestionGenerationResult("openai", openai.getModel(), parseDrafts(outputJson));
        } catch (RestClientException ex) {
            throw new AiProviderException("openai_request_failed", ex.getMessage());
        }
    }

    @Override
    public AiMiniLectureResult generateMiniLecture(MiniLectureGenerationRequest request) {
        AiProviderProperties.Provider openai = properties.getOpenai();
        requireConfigured(openai.getApiKey(), "openai_api_key_missing");
        String model = miniLectureOpenAiModel(openai);
        String userPrompt = promptBuilder.miniLecturePrompt(request);
        log.info(
                "OpenAI мини-лекция: POST /v1/responses, baseUrl={}, model={}, длина user-промпта≈{} симв.",
                openai.getBaseUrl(),
                model,
                userPrompt.length()
        );
        Map<String, Object> body = Map.of(
                "model", model,
                "input", List.of(
                        Map.of("role", "system", "content", promptBuilder.miniLectureSystemPrompt()),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "text", Map.of("format", Map.of(
                        "type", "json_schema",
                        "name", "damulab_mini_lecture_structured",
                        "strict", true,
                        "schema", miniLectureStructuredJsonSchema()
                ))
        );
        try {
            JsonNode response = restClientBuilder
                    .baseUrl(openai.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + openai.getApiKey())
                    .build()
                    .post()
                    .uri("/v1/responses")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String outputJson = extractOpenAiText(response == null ? objectMapper.createObjectNode() : response);
            log.debug("OpenAI мини-лекция: сырой JSON ответа, первые 400 симв.: {}", preview(outputJson, 400));
            MiniLectureStructuredPayload payload = parseMiniLectureStructured(outputJson);
            AiMiniLectureResult result = MiniLectureHtmlComposer.toResult(payload);
            log.info(
                    "OpenAI мини-лекция: успех, JSON≈{} симв., HTML ru={} kk={}",
                    outputJson.length(),
                    result.contentRu().length(),
                    result.contentKk().length()
            );
            return result;
        } catch (AiProviderException ex) {
            log.error(
                    "OpenAI мини-лекция: ошибка разбора/схемы code={} message={}",
                    ex.getCode(),
                    ex.getMessage()
            );
            throw ex;
        } catch (RestClientException ex) {
            log.error("OpenAI мини-лекция: HTTP/сеть — {}", ex.getMessage(), ex);
            throw new AiProviderException("openai_request_failed", ex.getMessage());
        }
    }

    private static String preview(String text, int max) {
        if (text == null) {
            return "(null)";
        }
        String t = text.replace('\n', ' ');
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    private String miniLectureOpenAiModel(AiProviderProperties.Provider openai) {
        String configured = properties.getMiniLecture().getOpenaiModel();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return openai.getModel();
    }

    private void requireConfigured(String value, String code) {
        if (value == null || value.isBlank()) {
            throw new AiProviderException(code, code);
        }
    }
}
