package kz.damulab.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class ExternalAiProviderSupport {

    private static final Logger log = LoggerFactory.getLogger(ExternalAiProviderSupport.class);

    private final ObjectMapper objectMapper;
    private final AiDraftSchemaValidator validator;

    protected ExternalAiProviderSupport(ObjectMapper objectMapper, AiDraftSchemaValidator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    protected AiMiniLectureResult finalizeMiniLecture(
            String outputJson,
            MiniLectureGenerationRequest request,
            String operation,
            String model,
            int attempt
    ) {
        AiCallLogger.logInboundRaw(log, operation, model, attempt, outputJson);
        MiniLectureStructuredPayload payload = parseMiniLectureStructured(outputJson);
        AiCallLogger.logMiniLectureParsed(log, operation, payload);
        MiniLectureQualityValidator.validate(payload, request);
        return MiniLectureHtmlComposer.toResult(payload);
    }

    protected static boolean isMiniLectureQualityFailure(AiProviderException ex) {
        return "ai_mini_lecture_too_brief".equals(ex.getCode());
    }

    protected Map<String, Object> miniLectureStructuredJsonSchema() {
        Map<String, Object> langBlock = objectSchema(Map.of(
                "title", stringSchema(),
                "theory", stringSchema(),
                "question_analysis", stringSchema(),
                "common_mistake", stringSchema(),
                "example_analysis", stringSchema(),
                "summary", stringSchema()
        ), List.of(
                "title", "theory", "question_analysis", "common_mistake", "example_analysis", "summary"
        ));
        return objectSchema(Map.of(
                "ru", langBlock,
                "kz", langBlock
        ), List.of("ru", "kz"));
    }

    protected MiniLectureStructuredPayload parseMiniLectureStructured(String json) {
        return MiniLectureJsonParser.parse(objectMapper, json);
    }

    protected List<AiGeneratedQuestionDraft> parseDrafts(String json) {
        try {
            ExternalAiQuestionPayload payload = objectMapper.readValue(json, ExternalAiQuestionPayload.class);
            payload.getQuestions().forEach(validator::validate);
            return payload.getQuestions();
        } catch (JsonProcessingException | AiProviderException ex) {
            throw new AiProviderException("ai_schema_invalid", ex.getMessage());
        }
    }

    protected String extractOpenAiText(JsonNode response) {
        if (response.hasNonNull("output_text")) {
            return response.get("output_text").asText();
        }
        JsonNode output = response.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.get("content");
                if (content != null && content.isArray()) {
                    for (JsonNode contentItem : content) {
                        if (contentItem.hasNonNull("text")) {
                            return contentItem.get("text").asText();
                        }
                    }
                }
            }
        }
        throw new AiProviderException("openai_response_empty", "OpenAI response did not contain output text");
    }

    protected String extractDeepSeekText(JsonNode response) {
        JsonNode choices = response.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode content = choices.get(0).path("message").path("content");
            if (!content.isMissingNode() && !content.isNull()) {
                return content.asText();
            }
        }
        throw new AiProviderException("deepseek_response_empty", "DeepSeek response did not contain message content");
    }

    protected Map<String, Object> questionJsonSchema() {
        Map<String, Object> option = objectSchema(Map.of(
                "label", stringSchema(),
                "textRu", stringSchema(),
                "textKk", stringSchema(),
                "correct", Map.of("type", "boolean")
        ), List.of("label", "textRu", "textKk", "correct"));
        Map<String, Object> matchingPair = objectSchema(Map.of(
                "leftRu", stringSchema(),
                "leftKk", stringSchema(),
                "rightRu", stringSchema(),
                "rightKk", stringSchema()
        ), List.of("leftRu", "leftKk", "rightRu", "rightKk"));
        Map<String, Object> fillAnswer = objectSchema(Map.of(
                "placeholder", stringSchema(),
                "answer", stringSchema(),
                "matchMode", Map.of("type", "string", "enum", List.of("EXACT", "NORMALIZED", "NUMERIC_TOLERANCE", "REGEXP")),
                "tolerance", Map.of("type", List.of("number", "null"))
        ), List.of("placeholder", "answer", "matchMode", "tolerance"));
        Map<String, Object> draft = objectSchema(Map.ofEntries(
                Map.entry("questionType", Map.of("type", "string", "enum", List.of("SCQ", "MCQ", "MATCHING", "FILL_IN"))),
                Map.entry("difficulty", Map.of("type", "integer", "minimum", 1, "maximum", 5)),
                Map.entry("bodyRu", stringSchema()),
                Map.entry("bodyKk", stringSchema()),
                Map.entry("explanationRu", stringSchema()),
                Map.entry("explanationKk", stringSchema()),
                Map.entry("source", stringSchema()),
                Map.entry("options", arraySchema(option)),
                Map.entry("matchingPairs", arraySchema(matchingPair)),
                Map.entry("fillAnswers", arraySchema(fillAnswer)),
                Map.entry("qualityScore", Map.of("type", "integer", "minimum", 0, "maximum", 100)),
                Map.entry("qualityNotes", stringSchema()),
                Map.entry("flags", arraySchema(stringSchema()))
        ), List.of(
                "questionType", "difficulty", "bodyRu", "bodyKk", "explanationRu", "explanationKk", "source",
                "options", "matchingPairs", "fillAnswers", "qualityScore", "qualityNotes", "flags"
        ));
        return objectSchema(Map.of("questions", arraySchema(draft)), List.of("questions"));
    }

    private Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    private Map<String, Object> arraySchema(Map<String, Object> item) {
        return Map.of("type", "array", "items", item);
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }
}
