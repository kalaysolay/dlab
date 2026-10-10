package kz.damulab.quiz.engine;

import java.math.*;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import kz.damulab.quiz.*;
import org.springframework.stereotype.Component;

/** Расчёт из неизменяемых снимков и окончательных ответов; скорость влияет только на место, а не на баллы. */
@Component
public class QuizResultCalculator {
    private final QuizRoundAnswerChecker checker;
    private final QuizSnapshotJson json;
    /** Подключает зависимости движка; создание компонента не читает и не изменяет БД. */
    public QuizResultCalculator(QuizRoundAnswerChecker checker, QuizSnapshotJson json) { this.checker = checker; this.json = json; }
    /** Возвращает стабильный рейтинг: баллы ↓, время ↑, joinedAt ↑, ID как детерминированный последний порядок. */
    public List<Standing> calculate(QuizRoom room, List<QuizRound> rounds, List<QuizParticipant> participants, List<QuizAnswer> answers) {
        List<Standing> values = new ArrayList<>();
        for (var participant : participants) {
            Map<Long, QuizAnswer> own = ownAnswers(participant, answers); int correct = 0; long time = 0; boolean unknownTime = false;
            BigDecimal score = BigDecimal.ZERO, max = BigDecimal.ZERO;
            for (var round : rounds) {
                var answer = own.get(round.getId()); boolean ok = correct(round, answer);
                if (ok) correct++;
                boolean legacy = room.getGameVersion() == null;
                BigDecimal points = legacy ? round.getPoints() : BigDecimal.ONE;
                max = max.add(points);
                if (ok) score = score.add(legacy ? answer.getPointsAwarded() : BigDecimal.ONE);
                // Ранний skip не выигрывает ничью; время принятия хранится отдельно. Legacy null не выдумываем.
                if (legacy) unknownTime = true;
                else if (answer != null && answer.getResponseMs() == null) throw new QuizEngineException("ANSWER_TIMING_INVALID");
                else if (answer == null || answer.isSkipped() || answer.isTimedOut()) time += room.getRoundSeconds() * 1000L;
                else {
                    if (answer.getResponseMs() < 0 || answer.getResponseMs() >= room.getRoundSeconds() * 1000L) throw new QuizEngineException("ANSWER_TIMING_INVALID");
                    time += answer.getResponseMs();
                }
            }
            int percent = max.signum() == 0 ? 0 : score.multiply(BigDecimal.valueOf(100)).divide(max, 0, RoundingMode.HALF_UP).intValue();
            values.add(new Standing(participant, 0, rounds.size(), correct, score, max, percent, unknownTime ? null : time));
        }
        // Если достоверного времени нет, сохраняем старую сортировку и не сравниваем выдуманные нули.
        boolean timed = room.getGameVersion() != null && values.stream().allMatch(s -> s.totalResponseMs() != null);
        Comparator<Standing> order = Comparator.comparing(Standing::score, Comparator.reverseOrder());
        if (timed) order = order.thenComparing(Standing::totalResponseMs);
        else if (room.getGameVersion() == null) order = order.thenComparing(Standing::percent, Comparator.reverseOrder());
        order = order.thenComparing(s -> s.participant().getJoinedAt()).thenComparing(s -> s.participant().getId());
        values.sort(order); List<Standing> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) { var v = values.get(i); result.add(new Standing(v.participant(), i + 1, v.total(), v.correct(), v.score(), v.maxScore(), v.percent(), v.totalResponseMs())); }
        return List.copyOf(result);
    }
    /** Строит только личный разбор после FINISHED; остальные ответы не включаются. */
    public List<Review> review(QuizRoom room, QuizParticipant participant, List<QuizRound> rounds, List<QuizAnswer> answers) {
        if (room.getStatus() != QuizRoomStatus.FINISHED || !Objects.equals(participant.getRoom().getId(), room.getId())) throw new QuizEngineException("RESULTS_NOT_AVAILABLE");
        Map<Long, QuizAnswer> own = ownAnswers(participant, answers);
        return rounds.stream().map(round -> {
            if (round.getPromptSnapshot() == null) throw new QuizEngineException("LEGACY_ROOM");
            var answer = own.get(round.getId());
            return new Review(round.getId(), round.getOrderNo(), json.read(round.getSourceConfigSnapshotJson()).path("format").asText(), round.getPromptSnapshot(),
                    json.read(round.getOptionsSnapshotJson()), answer == null ? null : json.readNullable(answer.getAnswerJson()), correct(round, answer),
                    answer != null && answer.isSkipped(), answer == null || answer.isTimedOut(), answer == null ? null : answer.getResponseMs(),
                    checker.correctAnswer(round), round.getExplanationSnapshot(), round.getSourceReferenceSnapshot());
        }).toList();
    }
    private boolean correct(QuizRound round, QuizAnswer answer) {
        if (answer == null || answer.isSkipped() || answer.isTimedOut()) return false;
        if (round.getPromptSnapshot() == null) return answer.isCorrect();
        return checker.check(round, json.readNullable(answer.getAnswerJson())).correct();
    }
    private static Map<Long, QuizAnswer> ownAnswers(QuizParticipant participant, List<QuizAnswer> answers) {
        Map<Long, QuizAnswer> result = new HashMap<>();
        answers.stream().filter(a -> a.getParticipant().getId().equals(participant.getId())).forEach(a -> {
            if (result.put(a.getRound().getId(), a) != null) throw new QuizEngineException("DUPLICATE_ANSWER");
        }); return result;
    }
    /** Внутренний расчёт; JPA participant не выдаётся публично. */
    public record Standing(QuizParticipant participant, int place, int total, int correct, BigDecimal score, BigDecimal maxScore, int percent, Long totalResponseMs) {}
    /** Личный итоговый DTO: закрытые поля разрешены только после FINISHED и проверки membership. */
    public record Review(Long roundId, int orderNo, String format, String prompt, JsonNode options, JsonNode answer, boolean correct,
                         boolean skipped, boolean timedOut, Long responseMs, JsonNode correctAnswer, String explanation, String sourceReference) {}
}
