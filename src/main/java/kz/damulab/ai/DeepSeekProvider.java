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
        Map<String, Object> body = Map.of(
                "model", deepseek.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", promptBuilder.systemPrompt()),
                        Map.of("role", "user", "content", promptBuilder.questionGenerationPrompt(request)
                                + "\nReturn JSON only with root object {\"questions\": [...]} and no markdown.")
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.2
        );
        try {
            JsonNode response = restClientBuilder
                    .baseUrl(deepseek.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + deepseek.getApiKey())
                    .build()
                    .post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String outputJson = extractDeepSeekText(response == null ? objectMapper.createObjectNode() : response);
            return new AiQuestionGenerationResult("deepseek", deepseek.getModel(), parseDrafts(outputJson));
        } catch (RestClientException ex) {
            throw new AiProviderException("deepseek_request_failed", ex.getMessage());
        }
    }

    @Override
    public AiMiniLectureResult generateMiniLecture(MiniLectureGenerationRequest request) {
        AiProviderProperties.Provider deepseek = properties.getDeepseek();
        requireConfigured(deepseek.getApiKey(), "deepseek_api_key_missing");
        String model = miniLectureDeepSeekModel(deepseek);
        String userContent = promptBuilder.miniLecturePrompt(request)
                + """
                
                Return JSON only (no markdown). Root object must have keys "ru" and "kz".
                Each value is an object with string fields: title, theory, question_analysis, common_mistake, example_analysis, summary.
                """;
        log.info(
                "DeepSeek мини-лекция: POST /chat/completions, baseUrl={}, model={}, длина user-сообщения≈{} симв.",
                deepseek.getBaseUrl(),
                model,
                userContent.length()
        );
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", promptBuilder.miniLectureSystemPrompt()),
                        Map.of("role", "user", "content", userContent)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.25
        );
        try {
            JsonNode response = restClientBuilder
                    .baseUrl(deepseek.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + deepseek.getApiKey())
                    .build()
                    .post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String outputJson = extractDeepSeekText(response == null ? objectMapper.createObjectNode() : response);
            log.debug("DeepSeek мини-лекция: сырой JSON, первые 400 симв.: {}", preview(outputJson, 400));
            MiniLectureStructuredPayload payload = parseMiniLectureStructured(outputJson);
            AiMiniLectureResult result = MiniLectureHtmlComposer.toResult(payload);
            log.info(
                    "DeepSeek мини-лекция: успех, JSON≈{} симв., HTML ru={} kk={}",
                    outputJson.length(),
                    result.contentRu().length(),
                    result.contentKk().length()
            );
            return result;
        } catch (AiProviderException ex) {
            log.error(
                    "DeepSeek мини-лекция: разбор/схема code={} message={}",
                    ex.getCode(),
                    ex.getMessage()
            );
            throw ex;
        } catch (RestClientException ex) {
            log.error("DeepSeek мини-лекция: HTTP/сеть — {}", ex.getMessage(), ex);
            throw new AiProviderException("deepseek_request_failed", ex.getMessage());
        }
    }

    private static String preview(String text, int max) {
        if (text == null) {
            return "(null)";
        }
        String t = text.replace('\n', ' ');
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    private String miniLectureDeepSeekModel(AiProviderProperties.Provider deepseek) {
        String configured = properties.getMiniLecture().getDeepseekModel();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return deepseek.getModel();
    }

    private void requireConfigured(String value, String code) {
        if (value == null || value.isBlank()) {
            throw new AiProviderException(code, code);
        }
    }
}
