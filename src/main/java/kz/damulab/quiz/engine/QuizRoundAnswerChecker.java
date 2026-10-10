package kz.damulab.quiz.engine;

import java.math.BigDecimal;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import kz.damulab.quiz.QuizRound;
import kz.damulab.quiz.catalog.QuizContentKind;
import kz.damulab.questions.QuestionType;
import kz.damulab.testing.AnswerChecker;
import org.springframework.stereotype.Component;

/** Единый checker снимков: предметные форматы делегируются прежнему AnswerChecker, новые типы проверяются отдельно. */
@Component
public class QuizRoundAnswerChecker {
    private final QuizSnapshotJson json;
    private final AnswerChecker subject;
    /** Подключает зависимости движка; создание компонента не читает и не изменяет БД. */
    public QuizRoundAnswerChecker(QuizSnapshotJson json, AnswerChecker subject) { this.json = json; this.subject = subject; }
    /** Проверяет содержательный ответ; null/пустой payload не является false, skip или timeout. Балл всегда 0/1. */
    public Checked check(QuizRound round, JsonNode answer) {
        JsonNode canonical = canonical(round, answer); boolean correct;
        if (round.getContentKind() == QuizContentKind.QUESTION) {
            correct = subject.check(format(round), round.getAnswerKeySnapshotJson(), answer, BigDecimal.ONE).correct();
        } else if (round.getContentKind() == QuizContentKind.FACT) {
            JsonNode key = json.read(round.getAnswerKeySnapshotJson());
            if (!key.isBoolean()) throw new QuizEngineException("SNAPSHOT_INVALID");
            correct = key.booleanValue() == answer.booleanValue();
        } else {
            correct = json.read(round.getAnswerKeySnapshotJson()).asText().equals(canonical.path("selected").get(0).asText());
        }
        return new Checked(correct, correct ? BigDecimal.ONE : BigDecimal.ZERO, json.write(canonical));
    }
    /** Канонизирует retry: порядок MCQ/пар несущественен, текст FILL_IN сохраняется без новой нормализации. */
    public JsonNode canonical(QuizRound round, JsonNode answer) {
        if (answer == null || answer.isNull()) throw new QuizEngineException("ANSWER_REQUIRED");
        if (round.getContentKind() == QuizContentKind.FACT) {
            require(answer.isBoolean()); return answer.deepCopy();
        }
        require(answer.isObject());
        QuestionType type = round.getContentKind() == QuizContentKind.VOCABULARY ? QuestionType.SCQ : format(round);
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        switch (type) {
            case SCQ, MCQ -> {
                require(answer.size() == 1 && answer.has("selected"));
                JsonNode selected = answer.path("selected"); TreeSet<String> ids = new TreeSet<>();
                if (selected.isTextual()) ids.add(selected.asText().trim().toUpperCase(Locale.ROOT));
                else { require(selected.isArray()); selected.forEach(n -> { require(n.isTextual()); ids.add(n.asText().trim().toUpperCase(Locale.ROOT)); }); }
                require(!ids.isEmpty() && (type != QuestionType.SCQ || ids.size() == 1));
                Set<String> allowed = new HashSet<>(); json.read(round.getOptionsSnapshotJson()).forEach(n -> allowed.add(n.path("id").asText()));
                require(allowed.containsAll(ids)); ArrayNode values = result.putArray("selected"); ids.forEach(values::add);
            }
            case MATCHING -> {
                require(answer.size() == 1 && answer.path("pairs").isObject() && !answer.path("pairs").isEmpty());
                TreeMap<String, String> pairs = new TreeMap<>();
                answer.path("pairs").fields().forEachRemaining(e -> {
                    require(e.getValue().isTextual()); String left = normalizePair(e.getKey()), right = normalizePair(e.getValue().asText());
                    require(!left.isEmpty() && !right.isEmpty() && pairs.put(left, right) == null);
                });
                result.set("pairs", JsonNodeFactory.instance.objectNode()); pairs.forEach((l, r) -> ((ObjectNode) result.get("pairs")).put(l, r));
            }
            case FILL_IN -> {
                require(answer.size() == 1 && answer.path("answers").isObject() && !answer.path("answers").isEmpty());
                TreeMap<String, String> fields = new TreeMap<>();
                answer.path("answers").fields().forEachRemaining(e -> { require(e.getValue().isTextual()); fields.put(e.getKey(), e.getValue().asText()); });
                ObjectNode values = result.putObject("answers"); fields.forEach(values::put);
            }
        }
        return result;
    }
    /** Возвращает тип из снимка; не читает текущий QuestionVersion. */
    public QuestionType format(QuizRound round) {
        try { return QuestionType.valueOf(json.read(round.getSourceConfigSnapshotJson()).path("format").asText()); }
        catch (IllegalArgumentException e) { throw new QuizEngineException("SNAPSHOT_INVALID"); }
    }
    /** Возвращает понятный правильный ответ для финального разбора, без технических правил FILL_IN. */
    public JsonNode correctAnswer(QuizRound round) {
        JsonNode key = json.read(round.getAnswerKeySnapshotJson());
        if (round.getContentKind() == QuizContentKind.QUESTION && format(round) == QuestionType.FILL_IN) {
            ObjectNode values = JsonNodeFactory.instance.objectNode(); key.fields().forEachRemaining(e -> values.put(e.getKey(), e.getValue().path("answer").asText()));
            return values;
        }
        return key;
    }
    private static String normalizePair(String s) { return s.trim().replace(',', '.').replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    private static void require(boolean valid) { if (!valid) throw new QuizEngineException("ANSWER_INVALID"); }
    /** Закрытый результат проверки для сохранения; не использовать как публичное подтверждение принятия. */
    public record Checked(boolean correct, BigDecimal points, String canonicalJson) {}
}
