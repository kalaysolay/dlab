package kz.damulab.testing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import kz.damulab.questions.QuestionType;
import kz.damulab.questions.QuestionVersion;

@Component
public class AnswerChecker {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Map<String, String>>> FILL_KEY = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public AnswerChecker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnswerCheckResult check(QuestionVersion version, JsonNode answer, BigDecimal points) {
        boolean correct = switch (version.getType()) {
            case SCQ -> checkChoice(version, answer, true);
            case MCQ -> checkChoice(version, answer, false);
            case MATCHING -> checkMatching(version, answer);
            case FILL_IN -> checkFillIn(version, answer);
        };
        BigDecimal awarded = correct ? points : BigDecimal.ZERO;
        return new AnswerCheckResult(correct, awarded, detailsJson(correct, awarded));
    }

    private boolean checkChoice(QuestionVersion version, JsonNode answer, boolean single) {
        Set<String> expected = new HashSet<>(read(version.getAnswerKeyJson(), STRING_LIST));
        Set<String> submitted = new HashSet<>();
        JsonNode selected = answer.path("selected");
        if (selected.isArray()) {
            selected.forEach(value -> submitted.add(value.asText().trim().toUpperCase(Locale.ROOT)));
        } else if (selected.isTextual()) {
            submitted.add(selected.asText().trim().toUpperCase(Locale.ROOT));
        }
        return (!single || submitted.size() == 1) && submitted.equals(expected);
    }

    private boolean checkMatching(QuestionVersion version, JsonNode answer) {
        Map<String, String> expected = new LinkedHashMap<>();
        JsonNode pairs = readTree(version.getAnswerKeyJson()).path("pairs");
        pairs.forEach(pair -> expected.put(normalize(pair.path("left").asText()), normalize(pair.path("right").asText())));

        Map<String, String> submitted = new LinkedHashMap<>();
        JsonNode submittedPairs = answer.path("pairs");
        if (submittedPairs.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = submittedPairs.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                submitted.put(normalize(field.getKey()), normalize(field.getValue().asText()));
            }
        }
        return submitted.equals(expected);
    }

    private boolean checkFillIn(QuestionVersion version, JsonNode answer) {
        Map<String, Map<String, String>> expected = read(version.getAnswerKeyJson(), FILL_KEY);
        JsonNode submittedAnswers = answer.path("answers");
        if (!submittedAnswers.isObject()) {
            return false;
        }
        for (Map.Entry<String, Map<String, String>> entry : expected.entrySet()) {
            String submitted = submittedAnswers.path(entry.getKey()).asText(null);
            if (submitted == null || !matchesFillAnswer(submitted, entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesFillAnswer(String submitted, Map<String, String> rule) {
        String expected = rule.getOrDefault("answer", "");
        String mode = rule.getOrDefault("mode", "EXACT");
        return switch (mode) {
            case "NORMALIZED" -> normalize(submitted).equals(normalize(expected));
            case "NUMERIC_TOLERANCE" -> withinTolerance(submitted, expected, rule.get("tolerance"));
            case "REGEXP" -> Pattern.compile(expected, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(submitted.trim())
                    .matches();
            default -> submitted.trim().equals(expected.trim());
        };
    }

    private boolean withinTolerance(String submitted, String expected, String tolerance) {
        try {
            BigDecimal submittedNumber = new BigDecimal(submitted.trim().replace(',', '.'));
            BigDecimal expectedNumber = new BigDecimal(expected.trim().replace(',', '.'));
            BigDecimal toleranceNumber = tolerance == null || tolerance.isBlank()
                    ? BigDecimal.ZERO
                    : new BigDecimal(tolerance.trim().replace(',', '.'));
            return submittedNumber.subtract(expectedNumber).abs().compareTo(toleranceNumber) <= 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    Object publicCorrectAnswer(QuestionVersion version) {
        return switch (version.getType()) {
            case SCQ, MCQ -> read(version.getAnswerKeyJson(), STRING_LIST);
            case MATCHING -> readTree(version.getAnswerKeyJson()).path("pairs");
            case FILL_IN -> objectMapper.valueToTree(fillAnswerValues(version.getAnswerKeyJson()));
        };
    }

    private Map<String, String> fillAnswerValues(String answerKeyJson) {
        Map<String, Map<String, String>> rules = read(answerKeyJson, FILL_KEY);
        Map<String, String> values = new LinkedHashMap<>();
        rules.forEach((placeholder, rule) -> values.put(placeholder, rule.get("answer")));
        return values;
    }

    private String detailsJson(boolean correct, BigDecimal pointsAwarded) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "correct", correct,
                    "pointsAwarded", pointsAwarded
            ));
        } catch (JsonProcessingException ex) {
            throw new TestingHubException("answer_evaluation_invalid");
        }
    }

    /**
     * Строки для страницы результата теста: сопоставление «левая колонка → ответ ученика → верный ответ».
     */
    public List<MatchingResultRow> matchingResultRows(QuestionVersion version, String answerJson, String language) {
        if (version.getType() != QuestionType.MATCHING) {
            return List.of();
        }
        JsonNode options = readTree(version.getOptionsJson());
        JsonNode keyPairs = readTree(version.getAnswerKeyJson()).path("pairs");
        Map<String, String> submittedByLeftNorm = new LinkedHashMap<>();
        JsonNode root = readTree(answerJson);
        JsonNode sp = root.path("pairs");
        if (sp.isObject()) {
            sp.fields().forEachRemaining(e ->
                    submittedByLeftNorm.put(normalize(e.getKey()), normalize(e.getValue().asText())));
        }
        List<MatchingResultRow> rows = new ArrayList<>();
        int pairCount = keyPairs.size();
        for (int index = 0; index < pairCount; index++) {
            JsonNode keyPair = keyPairs.get(index);
            JsonNode optionPair = index < options.size() ? options.get(index) : null;
            if (optionPair == null) {
                continue;
            }
            String leftNorm = normalize(keyPair.path("left").asText());
            String rightNorm = normalize(keyPair.path("right").asText());
            String leftLabel = localized(optionPair, "left", language);
            String correctRightLabel = localized(optionPair, "right", language);
            String studentRightNorm = submittedByLeftNorm.get(leftNorm);
            String studentRightLabel;
            if (studentRightNorm == null || studentRightNorm.isEmpty()) {
                studentRightLabel = "—";
            } else {
                studentRightLabel = findRightLabelForNormalizedValue(options, studentRightNorm, language);
            }
            boolean rowOk = studentRightNorm != null && studentRightNorm.equals(rightNorm);
            rows.add(new MatchingResultRow(leftLabel, studentRightLabel, correctRightLabel, rowOk));
        }
        return rows;
    }

    private String findRightLabelForNormalizedValue(JsonNode options, String normalizedRightValue, String language) {
        for (JsonNode pair : options) {
            if (matchesRightValue(pair, normalizedRightValue)) {
                return localized(pair, "right", language);
            }
        }
        return normalizedRightValue;
    }

    private boolean matchesRightValue(JsonNode pair, String normalizedRightValue) {
        String ru = normalize(pair.path("rightRu").asText());
        String kk = normalize(pair.path("rightKk").asText());
        return normalizedRightValue.equals(ru) || normalizedRightValue.equals(kk);
    }

    public List<ChoiceDisplay> choices(QuestionVersion version, String language) {
        if (version.getOptionsJson() == null || version.getOptionsJson().isBlank()) {
            return List.of();
        }
        JsonNode options = readTree(version.getOptionsJson());
        List<ChoiceDisplay> result = new ArrayList<>();
        options.forEach(option -> result.add(new ChoiceDisplay(
                option.path("label").asText(),
                localized(option, "text", language)
        )));
        return result;
    }

    public List<MatchingDisplay> matchingLeft(QuestionVersion version, String language) {
        return matchingSide(version, language, "left");
    }

    public List<MatchingDisplay> matchingRight(QuestionVersion version, String language) {
        List<MatchingDisplay> right = new ArrayList<>(matchingSide(version, language, "right"));
        java.util.Collections.reverse(right);
        return right;
    }

    public List<String> fillPlaceholders(QuestionVersion version) {
        if (version.getType() != QuestionType.FILL_IN) {
            return List.of();
        }
        return new ArrayList<>(read(version.getAnswerKeyJson(), FILL_KEY).keySet());
    }

    private List<MatchingDisplay> matchingSide(QuestionVersion version, String language, String side) {
        if (version.getOptionsJson() == null || version.getOptionsJson().isBlank()) {
            return List.of();
        }
        List<MatchingDisplay> result = new ArrayList<>();
        JsonNode pairs = readTree(version.getOptionsJson());
        pairs.forEach(pair -> {
            String value = pair.path(side + "Ru").asText();
            result.add(new MatchingDisplay(value, localized(pair, side, language)));
        });
        return result;
    }

    Object submittedAnswer(String answerJson) {
        return readTree(answerJson);
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new TestingHubException("question_payload_invalid");
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new TestingHubException("question_payload_invalid");
        }
    }

    private String localized(JsonNode node, String prefix, String language) {
        String suffix = "kk".equals(language) ? "Kk" : "Ru";
        String value = node.path(prefix + suffix).asText();
        if (value == null || value.isBlank()) {
            return node.path(prefix + "Ru").asText();
        }
        return value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replace(',', '.').replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
