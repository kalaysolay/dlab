package kz.damulab.ai;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OpenAiProvider extends ExternalAiProviderSupport implements AiProvider {

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

    private void requireConfigured(String value, String code) {
        if (value == null || value.isBlank()) {
            throw new AiProviderException(code, code);
        }
    }
}
