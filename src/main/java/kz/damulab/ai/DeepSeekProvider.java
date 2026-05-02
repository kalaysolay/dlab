package kz.damulab.ai;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class DeepSeekProvider extends ExternalAiProviderSupport implements AiProvider {

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

    private void requireConfigured(String value, String code) {
        if (value == null || value.isBlank()) {
            throw new AiProviderException(code, code);
        }
    }
}
