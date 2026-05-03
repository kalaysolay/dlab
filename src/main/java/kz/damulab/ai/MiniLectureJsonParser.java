package kz.damulab.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class MiniLectureJsonParser {

    private MiniLectureJsonParser() {
    }

    public static MiniLectureStructuredPayload parse(ObjectMapper objectMapper, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return new MiniLectureStructuredPayload(
                    requireLangBlock(root.get("ru"), "ru"),
                    requireLangBlock(root.get("kz"), "kz")
            );
        } catch (JsonProcessingException ex) {
            throw new AiProviderException("ai_schema_invalid", ex.getMessage());
        }
    }

    private static MiniLectureLangBlock requireLangBlock(JsonNode node, String key) {
        if (node == null || !node.isObject()) {
            throw new AiProviderException("ai_schema_invalid", "Missing object: " + key);
        }
        return new MiniLectureLangBlock(
                requireNonBlankText(node, "title", key),
                requireNonBlankText(node, "theory", key),
                requireNonBlankText(node, "question_analysis", key),
                requireNonBlankText(node, "common_mistake", key),
                requireNonBlankText(node, "example_analysis", key),
                requireNonBlankText(node, "summary", key)
        );
    }

    private static String requireNonBlankText(JsonNode parent, String field, String parentKey) {
        String value = parent.path(field).asText("").trim();
        if (value.isEmpty()) {
            throw new AiProviderException("ai_schema_invalid", "Empty " + parentKey + "." + field);
        }
        return value;
    }
}
