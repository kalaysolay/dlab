package kz.damulab.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kz.damulab.questions.QuestionType;

/**
 * Общая логика внешних AI-провайдеров: схемы JSON, разбор ответа, валидация черновиков.
 * OpenAI держит контракт через strict {@code json_schema}; DeepSeek — через промпт + мягкий разбор
 * ({@link #parseDrafts(String, QuestionType)}, {@link #questionSchemaPromptAppendix()}).
 */
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

    /**
     * Разбор ответа провайдера со строгой схемой (OpenAI). Без подстановки типа из запроса.
     */
    protected List<AiGeneratedQuestionDraft> parseDrafts(String json) {
        return parseDrafts(json, null);
    }

    /**
     * Разбор ответа LLM в список черновиков.
     *
     * @param json             сырой текст (иногда в markdown fence — снимаем)
     * @param defaultQuestionType если модель забыла {@code questionType}, подставляем тип из формы
     *                            генерации (у запроса всегда один целевой тип). {@code null} — не подставлять.
     */
    protected List<AiGeneratedQuestionDraft> parseDrafts(String json, QuestionType defaultQuestionType) {
        try {
            String cleaned = unwrapJsonPayload(json);
            ExternalAiQuestionPayload payload = objectMapper.readValue(cleaned, ExternalAiQuestionPayload.class);
            List<AiGeneratedQuestionDraft> raw = payload.getQuestions();
            if (raw == null || raw.isEmpty()) {
                throw new AiProviderException("ai_schema_invalid", "questions array is empty");
            }
            List<AiGeneratedQuestionDraft> drafts = new ArrayList<>(raw.size());
            for (AiGeneratedQuestionDraft draft : raw) {
                AiGeneratedQuestionDraft normalized = applyDefaultQuestionType(draft, defaultQuestionType);
                validator.validate(normalized);
                drafts.add(normalized);
            }
            return drafts;
        } catch (JsonProcessingException | AiProviderException ex) {
            throw new AiProviderException("ai_schema_invalid", ex.getMessage());
        }
    }

    /**
     * Текст со схемой полей для промпта DeepSeek (у API нет OpenAI-style strict json_schema).
     * Без этого модель часто отдаёт snake_case или пропускает {@code questionType}.
     */
    protected String questionSchemaPromptAppendix(QuestionType expectedType) {
        String schemaJson;
        try {
            schemaJson = objectMapper.writeValueAsString(questionJsonSchema());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize question JSON schema for prompt", ex);
        }
        String typeName = expectedType == null ? "SCQ|MCQ|MATCHING|FILL_IN" : expectedType.name();
        return """

                Return JSON only (no markdown fences). Root object MUST be {"questions":[...]} .
                Each element of "questions" MUST use camelCase field names exactly as in this JSON Schema:
                %s
                Every question MUST include "questionType" with value "%s" (same as the requested type).
                Do not use snake_case keys (question_type, body_ru, …).
                """.formatted(schemaJson, typeName);
    }

    /**
     * Снимает обёртку ``` / ```json, если модель всё же вернула markdown при json_object.
     */
    static String unwrapJsonPayload(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (!text.startsWith("```")) {
            return text;
        }
        int firstNewline = text.indexOf('\n');
        if (firstNewline < 0) {
            return text;
        }
        String withoutOpen = text.substring(firstNewline + 1);
        int fence = withoutOpen.lastIndexOf("```");
        if (fence >= 0) {
            withoutOpen = withoutOpen.substring(0, fence);
        }
        return withoutOpen.trim();
    }

    private static AiGeneratedQuestionDraft applyDefaultQuestionType(
            AiGeneratedQuestionDraft draft,
            QuestionType defaultQuestionType
    ) {
        if (draft == null || draft.questionType() != null || defaultQuestionType == null) {
            return draft;
        }
        log.warn(
                "AI draft missing questionType — using request type {}",
                defaultQuestionType
        );
        return new AiGeneratedQuestionDraft(
                defaultQuestionType,
                draft.difficulty(),
                draft.bodyRu(),
                draft.bodyKk(),
                draft.explanationRu(),
                draft.explanationKk(),
                draft.source(),
                draft.options(),
                draft.matchingPairs(),
                draft.fillAnswers(),
                draft.qualityScore(),
                draft.qualityNotes(),
                draft.flags()
        );
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
