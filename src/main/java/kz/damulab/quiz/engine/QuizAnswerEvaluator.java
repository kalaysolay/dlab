package kz.damulab.quiz.engine;

import java.math.BigDecimal;
import java.time.*;
import com.fasterxml.jackson.databind.JsonNode;
import kz.damulab.quiz.*;
import org.springframework.stereotype.Component;

/** Вычисляет окончательную запись ответа по снимку и серверному окну; QuizRoomService сохраняет её под lock и сравнивает retry. */
@Component
public class QuizAnswerEvaluator {
    private final QuizRoundAnswerChecker checker;
    /** Подключает зависимости движка; создание компонента не читает и не изменяет БД. */
    public QuizAnswerEvaluator(QuizRoundAnswerChecker checker) { this.checker = checker; }
    /** Создаёт содержательный ответ или явный skip внутри [start,end); отсутствие ответа отклоняется. */
    public QuizAnswer evaluate(QuizRound round, QuizParticipant participant, JsonNode answer, boolean skipped, OffsetDateTime acceptedAt) {
        requireParticipant(round, participant); OffsetDateTime start = startsAt(round), end = endsAt(round);
        if (acceptedAt.isBefore(start) || !acceptedAt.isBefore(end)) throw new QuizEngineException("ROUND_NOT_ACTIVE");
        if (skipped && answer != null && !answer.isNull()) throw new QuizEngineException("ANSWER_INVALID");
        var checked = skipped ? null : checker.check(round, answer);
        QuizAnswer value = new QuizAnswer(round, participant, skipped ? "null" : checked.canonicalJson(), checked != null && checked.correct(), checked == null ? BigDecimal.ZERO : checked.points());
        value.recordTiming(Duration.between(start, acceptedAt).toMillis(), false, skipped, acceptedAt); return value;
    }
    /** Создаёт timeout после конца окна; вызывающий сервис вставляет его только при отсутствии принятого ответа. */
    public QuizAnswer timeout(QuizRound round, QuizParticipant participant, OffsetDateTime now) {
        requireParticipant(round, participant);
        if (now.isBefore(endsAt(round))) throw new QuizEngineException("ROUND_NOT_ENDED");
        QuizAnswer value = new QuizAnswer(round, participant, "null", false, BigDecimal.ZERO);
        value.recordTiming(round.getRoom().getRoundSeconds() * 1000L, true, false, now); return value;
    }
    /** Возвращает единое начало раунда для всех участников. */
    public static OffsetDateTime startsAt(QuizRound round) {
        if (round.getRoom().getStartedAt() == null) throw new QuizEngineException("ROOM_NOT_STARTED");
        return round.getRoom().getStartedAt().plusSeconds((round.getOrderNo() - 1L) * round.getRoom().getRoundSeconds());
    }
    /** Возвращает исключённую правую границу серверного окна. */
    public static OffsetDateTime endsAt(QuizRound round) { return startsAt(round).plusSeconds(round.getRoom().getRoundSeconds()); }
    private static void requireParticipant(QuizRound round, QuizParticipant participant) {
        if (round.getRoom().getStatus() != QuizRoomStatus.ACTIVE || !round.getRoom().getId().equals(participant.getRoom().getId())) throw new QuizEngineException("PARTICIPANT_REQUIRED");
    }
}
