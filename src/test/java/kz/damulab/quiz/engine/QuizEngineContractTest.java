package kz.damulab.quiz.engine;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.*;
import kz.damulab.questions.*;
import kz.damulab.testing.AnswerChecker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;

/** Контракты всех checker, времени и рейтинга без БД; malformed ответы не подменяются пропуском. */
class QuizEngineContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final QuizSnapshotJson json = new QuizSnapshotJson(mapper);
    private final QuizRoundAnswerChecker checker = new QuizRoundAnswerChecker(json, new AnswerChecker(mapper));
    private final QuizAnswerEvaluator evaluator = new QuizAnswerEvaluator(checker);
    private final QuizResultCalculator calculator = new QuizResultCalculator(checker, json);
    private final OffsetDateTime start = OffsetDateTime.parse("2026-10-10T10:00:00Z");

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            SCQ|["B"]|{"selected":["B"]}|{"selected":["A"]}
            MCQ|["A","C"]|{"selected":["C","A"]}|{"selected":["A"]}
            MATCHING|{"pairs":[{"left":"50%","right":"0.5"},{"left":"25%","right":"0.25"}]}|{"pairs":{"25%":"0.25","50%":"0.5"}}|{"pairs":{"50%":"0.25","25%":"0.5"}}
            FILL_IN|{"[[1]]":{"answer":"30","mode":"EXACT"}}|{"answers":{"[[1]]":"30"}}|{"answers":{"[[1]]":"31"}}
            """)
    void subjectFormatsUseSnapshot(String format, String key, String good, String bad) {
        var round = subjectRound(room(), format, key);
        assertThat(checker.check(round, json.read(good)).correct()).isTrue();
        assertThat(checker.check(round, json.read(bad)).points()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(checker.check(round, json.read(good)).points()).isEqualByComparingTo(BigDecimal.ONE);
        if (format.equals("FILL_IN")) assertThat(checker.correctAnswer(round).toString()).doesNotContain("mode", "tolerance").contains("30");
    }
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            NORMALIZED| A  B |a b
            NUMERIC_TOLERANCE|30|30.1
            REGEXP|a[0-9]+|A12
            EXACT|30|30
            """)
    void existingFillModesRemain(String mode, String expected, String submitted) {
        var key = json.write(Map.of("[[1]]", Map.of("answer", expected, "mode", mode, "tolerance", "0.1")));
        assertThat(checker.check(subjectRound(room(), "FILL_IN", key), json.read(json.write(Map.of("answers", Map.of("[[1]]", submitted))))).correct()).isTrue();
    }
    @Test void falseTrueAndAbsenceAreDifferent() {
        var room = room(); var round = factRound(room, false, 1); var participant = participant(room, 1, start);
        assertThat(evaluator.evaluate(round, participant, json.read("false"), false, start.plusNanos(125000000)).getResponseMs()).isEqualTo(125);
        assertThat(checker.check(round, json.read("false")).correct()).isTrue();
        assertThat(checker.check(round, json.read("true")).correct()).isFalse();
        assertThat(checker.check(factRound(room, true, 1), json.read("true")).correct()).isTrue();
        assertThatThrownBy(() -> checker.check(round, null)).isInstanceOf(QuizEngineException.class).hasMessage("ANSWER_REQUIRED");
        for (String invalid : List.of("{}", "\"false\"", "0", "null", "{\"value\":false}"))
            assertThatThrownBy(() -> checker.check(round, json.readNullable(invalid))).isInstanceOf(QuizEngineException.class);
    }
    @Test void translationChecksChoiceNotText() {
        var round = QuizRound.vocabulary(room(), null, null, 1);
        round.snapshot("word", "[{\"id\":\"A\",\"text\":\"неверный\"},{\"id\":\"B\",\"text\":\"верный\"}]", "\"B\"", "Разбор", "Словарь", "{\"format\":\"TRANSLATION\"}", "ru");
        assertThat(checker.check(round, json.read("{\"selected\":[\"B\"]}")).correct()).isTrue();
        assertThat(checker.check(round, json.read("{\"selected\":[\"A\"]}")).correct()).isFalse();
        assertThatThrownBy(() -> checker.check(round, json.read("{\"selected\":[\"Z\"]}"))).hasMessage("ANSWER_INVALID");
    }
    @Test void canonicalRetryDoesNotChangeFillSemantics() {
        var round = subjectRound(room(), "MCQ", "[\"A\",\"C\"]");
        assertThat(checker.canonical(round, json.read("{\"selected\":[\"C\",\"A\",\"A\"]}"))).isEqualTo(checker.canonical(round, json.read("{\"selected\":[\"a\",\"c\"]}")));
        var matching = subjectRound(room(), "MATCHING", "{\"pairs\":[]}");
        assertThat(checker.canonical(matching, json.read("{\"pairs\":{\" X \":\" Y \",\"Z\":\"W\"}}"))).isEqualTo(checker.canonical(matching, json.read("{\"pairs\":{\"z\":\"w\",\"x\":\"y\"}}")));
        var fill = subjectRound(room(), "FILL_IN", "{\"[[1]]\":{\"answer\":\"A\"}}");
        assertThat(checker.canonical(fill, json.read("{\"answers\":{\"[[1]]\":\"A\"}}"))).isNotEqualTo(checker.canonical(fill, json.read("{\"answers\":{\"[[1]]\":\"a\"}}")));
    }
    @Test void windowIsSharedAndRightBoundaryExcluded() {
        var room = room(); var round = factRound(room, false, 2); var participant = participant(room, 1, start);
        assertThat(QuizAnswerEvaluator.startsAt(round)).isEqualTo(start.plusSeconds(10));
        assertThatThrownBy(() -> evaluator.evaluate(round, participant, json.read("false"), false, start.plusSeconds(9))).hasMessage("ROUND_NOT_ACTIVE");
        assertThat(evaluator.evaluate(round, participant, json.read("false"), false, start.plusSeconds(10)).getResponseMs()).isZero();
        assertThatThrownBy(() -> evaluator.evaluate(round, participant, json.read("false"), false, start.plusSeconds(20))).hasMessage("ROUND_NOT_ACTIVE");
        assertThatThrownBy(() -> evaluator.timeout(round, participant, start.plusSeconds(19))).hasMessage("ROUND_NOT_ENDED");
        assertThat(evaluator.timeout(round, participant, start.plusSeconds(20)).getResponseMs()).isEqualTo(10000);
    }
    @Test void skipTimeoutAreFinalAndDoNotAwardPoints() {
        var room = room(); var round = factRound(room, false, 1); var participant = participant(room, 1, start);
        var skip = evaluator.evaluate(round, participant, null, true, start.plusSeconds(1));
        var timeout = evaluator.timeout(round, participant, start.plusSeconds(50));
        assertThat(skip.isSkipped()).isTrue(); assertThat(skip.isTimedOut()).isFalse(); assertThat(skip.getResponseMs()).isEqualTo(1000);
        assertThat(timeout.isTimedOut()).isTrue(); assertThat(timeout.isSkipped()).isFalse(); assertThat(timeout.getResponseMs()).isEqualTo(10000);
        assertThat(timeout.getAnsweredAt()).isEqualTo(start.plusSeconds(50));
        assertThat(skip.getPointsAwarded()).isZero();
        assertThatThrownBy(() -> skip.replace("false", true, BigDecimal.ONE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> skip.recordTiming(2, false, false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> evaluator.evaluate(round, participant, json.read("false"), true, start)).hasMessage("ANSWER_INVALID");
    }
    @Test void ratingHasNoSpeedBonusAndSkipCostsFullLimit() {
        var room = room(); var r1 = factRound(room, false, 1); var r2 = factRound(room, true, 2); var r3 = factRound(room, false, 3);
        var slow = participant(room, 1, start); var fast = participant(room, 2, start.plusSeconds(1)); var later = participant(room, 3, start.plusSeconds(2));
        List<QuizAnswer> answers = new ArrayList<>();
        for (var p : List.of(slow, fast, later)) {
            answers.add(evaluator.evaluate(r1, p, json.read("false"), false, start.plusSeconds(p == slow ? 9 : 1)));
            answers.add(evaluator.evaluate(r2, p, null, true, start.plusSeconds(p == slow ? 10 : 19)));
            answers.add(evaluator.timeout(r3, p, start.plusSeconds(30)));
        }
        var rating = calculator.calculate(room, List.of(r1, r2, r3), List.of(slow, fast, later), answers);
        assertThat(rating).extracting(v -> v.participant().getId()).containsExactly(2L, 3L, 1L);
        assertThat(rating).extracting(QuizResultCalculator.Standing::score).containsOnly(BigDecimal.ONE);
        assertThat(rating).extracting(QuizResultCalculator.Standing::percent).containsOnly(33);
        assertThat(rating.get(0).totalResponseMs()).isEqualTo(21000);
        assertThat(rating.get(2).totalResponseMs()).isEqualTo(29000);
        room.finish(start.plusSeconds(30));
        assertThat(calculator.review(room, fast, List.of(r1, r2, r3), answers)).extracting(QuizResultCalculator.Review::correct).containsExactly(true, false, false);
    }
    @Test void missingAnswerUsesLimitAndLegacyTimeRemainsNull() {
        var room = room(); var r = factRound(room, false, 1); var p = participant(room, 1, start);
        assertThat(calculator.calculate(room, List.of(r), List.of(p), List.of()).getFirst().totalResponseMs()).isEqualTo(10000);
        var legacy = new QuizRoom("OLD", null, null, null, "ru", null, 1, 10, 4); setId(legacy, 2L);
        var oldRound = new QuizRound(legacy, null, 1, BigDecimal.ONE); setId(oldRound, 5L); var oldParticipant = participant(legacy, 1, start);
        var answer = new QuizAnswer(oldRound, oldParticipant, "{}", true, BigDecimal.ONE);
        var result = calculator.calculate(legacy, List.of(oldRound), List.of(oldParticipant), List.of(answer)).getFirst();
        assertThat(result.totalResponseMs()).isNull(); assertThat(result.score()).isEqualTo(BigDecimal.ONE); assertThat(answer.getResponseMs()).isNull();
    }
    @Test void snapshotsCannotBeOverwrittenAndReviewIsNotEarly() {
        var room = room(); var round = factRound(room, false, 1); var p = participant(room, 1, start);
        assertThatThrownBy(() -> round.snapshot("changed", "[]", "true", "changed", "changed", "{}", "ru")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> calculator.review(room, p, List.of(round), List.of())).hasMessage("RESULTS_NOT_AVAILABLE");
    }
    @Test void localizedBlankFallbackIsConsistent() {
        assertThat(QuizSnapshotJson.localized("RU", " ", "kk")).isEqualTo("RU");
        assertThat(QuizSnapshotJson.localized(" ", "KK", "ru")).isEqualTo("KK");
        assertThat(QuizSnapshotJson.localized(null, null, "kk")).isEmpty();
    }
    private QuizRoom room() {
        var room = new QuizRoom("NEW", null, null, null, "ru", null, 3, 10, 4); setId(room, 1L);
        room.attachGameVersion(new QuizGameVersion(1, null, QuizGameType.TRUE_FALSE, "Игра", "Ойын"), start.plusMinutes(30)); room.start(start); return room;
    }
    private QuizRound factRound(QuizRoom room, boolean key, int order) {
        var round = QuizRound.fact(room, null, order); round.snapshot("Факт", "[true,false]", String.valueOf(key), "Разбор", "Источник", "{\"format\":\"BOOLEAN\"}", "ru"); setId(round, (long) order); return round;
    }
    private QuizRound subjectRound(QuizRoom room, String format, String key) {
        var round = new QuizRound(room, null, 1, BigDecimal.ONE);
        round.snapshot("Вопрос", "[{\"id\":\"A\"},{\"id\":\"B\"},{\"id\":\"C\"}]", key, "Разбор", "Учебник", json.write(Map.of("format", format)), "ru"); return round;
    }
    private static QuizParticipant participant(QuizRoom room, long id, OffsetDateTime joinedAt) {
        var p = new QuizParticipant(room, null, "Ученик " + id, true); setId(p, id); ReflectionTestUtils.setField(p, "joinedAt", joinedAt); return p;
    }
    private static void setId(Object entity, Long id) { ReflectionTestUtils.setField(entity, "id", id); }
}
