package kz.damulab.quiz.engine;

import java.math.BigDecimal;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.*;
import kz.damulab.questions.*;
import org.springframework.stereotype.Component;

/** Предметные снимки сохраняют семантику AnswerChecker, но публичные варианты собираются по белому списку полей. */
@Component
public class SubjectRoundProvider implements QuizRoundProvider {
    private final QuizSubjectGameConfigRepository configs;
    private final QuestionVersionRepository versions;
    private final QuizSnapshotJson json;
    /** Подключает зависимости движка; создание компонента не читает и не изменяет БД. */
    public SubjectRoundProvider(QuizSubjectGameConfigRepository configs, QuestionVersionRepository versions, QuizSnapshotJson json) {
        this.configs = configs; this.versions = versions; this.json = json;
    }
    /** Обслуживает существующий предметный банк. */
    public QuizGameType gameType() { return QuizGameType.SUBJECT_QUIZ; }
    /** Отбирает текущие публикации нужного предмета, типа, сложности и пересечения всех классов. */
    public List<Source> prepare(QuizRoom room, Set<Long> required) {
        if (room.getGameVersion().getAudiencePolicy() == AudiencePolicy.UNIVERSAL || required.isEmpty()) throw new QuizEngineException("SUBJECT_UNIVERSAL_UNSUPPORTED");
        var config = configs.findById(room.getGameVersion().getId()).orElseThrow(() -> new QuizEngineException("SOURCE_UNAVAILABLE"));
        return versions.findPublishedForQuizSource(config.getSubject().getId()).stream()
                .filter(v -> v.getSubject().getId().equals(config.getSubject().getId()) && config.getQuestionTypes().contains(v.getType()))
                .filter(v -> config.getDifficulty() == null || config.getDifficulty() == v.getDifficulty())
                .filter(v -> QuizContentEligibility.subjectAudience(v, required) && QuizContentEligibility.subjectComplete(v))
                .map(v -> new Source(v.getId(), (order, random) -> snapshot(room, v, order, random))).toList();
    }
    private QuizRound snapshot(QuizRoom room, QuestionVersion v, int order, Random random) {
        JsonNode key = json.read(v.getAnswerKeyJson());
        Object options = publicOptions(v, key, room.getLanguage(), random);
        QuizRound round = new QuizRound(room, v, order, BigDecimal.ONE);
        round.snapshot(QuizSnapshotJson.localized(v.getBodyRu(), v.getBodyKk(), room.getLanguage()), json.write(options), json.write(key),
                QuizSnapshotJson.localized(v.getExplanationRu(), v.getExplanationKk(), room.getLanguage()), v.getSource(),
                json.write(Map.of("format", v.getType().name(), "sourceVersionId", v.getId(), "subjectId", v.getSubject().getId(), "difficulty", v.getDifficulty())), room.getLanguage());
        return round;
    }
    private Object publicOptions(QuestionVersion v, JsonNode key, String locale, Random random) {
        if (v.getType() == QuestionType.FILL_IN) {
            require(key.isObject() && !key.isEmpty()); List<String> placeholders = new ArrayList<>();
            key.fields().forEachRemaining(e -> {
                require(!e.getKey().isBlank() && e.getValue().isObject() && e.getValue().path("answer").isTextual() && !e.getValue().path("answer").asText().isBlank());
                validateFillRule(e.getValue());
                placeholders.add(e.getKey());
            });
            return Map.of("placeholders", placeholders);
        }
        JsonNode raw = json.read(v.getOptionsJson()); require(raw.isArray() && !raw.isEmpty());
        if (v.getType() == QuestionType.SCQ || v.getType() == QuestionType.MCQ) {
            require(raw.size() >= 2);
            require(key.isArray() && !key.isEmpty() && (v.getType() != QuestionType.SCQ || key.size() == 1));
            List<Map<String, String>> values = new ArrayList<>(); Set<String> ids = new HashSet<>();
            for (var option : raw) {
                String id = option.path("label").asText(); String text = localized(option, "text", locale);
                // Обязательные переводы публикации не заменяем fallback учебного контента.
                require(!option.path("textRu").asText().isBlank() && !option.path("textKk").asText().isBlank());
                require(!id.isBlank() && !text.isBlank() && ids.add(id)); values.add(Map.of("id", id, "text", text));
            }
            key.forEach(k -> require(k.isTextual() && ids.contains(k.asText())));
            Collections.shuffle(values, random); return values;
        }
        require(raw.size() >= 2 && key.path("pairs").isArray() && key.path("pairs").size() == raw.size());
        List<Map<String, String>> left = new ArrayList<>(), right = new ArrayList<>(); Set<String> leftIds = new HashSet<>(), rightIds = new HashSet<>();
        for (var pair : raw) {
            // Legacy checker принимает RU-значения в качестве идентификаторов, независимо от языка подписи.
            String l = pair.path("leftRu").asText(), r = pair.path("rightRu").asText();
            require(!pair.path("leftKk").asText().isBlank() && !pair.path("rightKk").asText().isBlank());
            require(!l.isBlank() && !r.isBlank() && leftIds.add(normalized(l)) && rightIds.add(normalized(r)));
            left.add(Map.of("id", l, "text", localized(pair, "left", locale)));
            right.add(Map.of("id", r, "text", localized(pair, "right", locale)));
        }
        Set<String> keyedLeft = new HashSet<>(), keyedRight = new HashSet<>();
        key.path("pairs").forEach(p -> {
            String l = normalized(p.path("left").asText()), r = normalized(p.path("right").asText());
            require(leftIds.contains(l) && rightIds.contains(r) && keyedLeft.add(l) && keyedRight.add(r));
        });
        // Парность исходного JSON никогда не выдаётся: обе колонки перемешаны независимо и фиксируются в снимке.
        Collections.shuffle(left, random); Collections.shuffle(right, random); return Map.of("left", left, "right", right);
    }
    private static String localized(JsonNode node, String prefix, String locale) {
        return QuizSnapshotJson.localized(node.path(prefix + "Ru").asText(), node.path(prefix + "Kk").asText(), locale);
    }
    private static String normalized(String value) { return value.trim().replace(',', '.').replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    private static void validateFillRule(JsonNode rule) {
        String mode = rule.path("mode").asText("EXACT");
        require(Set.of("EXACT", "NORMALIZED", "NUMERIC_TOLERANCE", "REGEXP").contains(mode));
        try {
            if (mode.equals("REGEXP")) java.util.regex.Pattern.compile(rule.path("answer").asText(), java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
            if (mode.equals("NUMERIC_TOLERANCE")) {
                new BigDecimal(rule.path("answer").asText().trim().replace(',', '.'));
                String tolerance = rule.path("tolerance").asText("0");
                if (!tolerance.isBlank()) require(new BigDecimal(tolerance.trim().replace(',', '.')).signum() >= 0);
            }
        } catch (IllegalArgumentException e) { throw new QuizEngineException("SNAPSHOT_INVALID"); }
    }
    private static void require(boolean valid) { if (!valid) throw new QuizEngineException("SNAPSHOT_INVALID"); }
}
