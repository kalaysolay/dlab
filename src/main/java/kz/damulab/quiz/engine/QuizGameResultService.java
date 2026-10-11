package kz.damulab.quiz.engine;

import java.math.BigDecimal;
import java.util.*;
import kz.damulab.quiz.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Сохраняет/читает результаты уже завершённой новой игры; QuizRoomService атомарно завершает расписание и вызывает save. */
@Service
public class QuizGameResultService {
    private final QuizRoomRepository rooms;
    private final QuizParticipantRepository participants;
    private final QuizRoundRepository rounds;
    private final QuizAnswerRepository answers;
    private final QuizResultRepository results;
    private final QuizResultCalculator calculator;
    /** Подключает зависимости движка; создание компонента не читает и не изменяет БД. */
    public QuizGameResultService(QuizRoomRepository rooms, QuizParticipantRepository participants, QuizRoundRepository rounds,
            QuizAnswerRepository answers, QuizResultRepository results, QuizResultCalculator calculator) {
        this.rooms = rooms; this.participants = participants; this.rounds = rounds; this.answers = answers; this.results = results; this.calculator = calculator;
    }
    /** Атомарно сохраняет результаты FINISHED-комнаты идемпотентно, без смены статуса. */
    @Transactional
    public void save(String code) {
        var room = rooms.findLockedByCode(code).orElseThrow(() -> new QuizEngineException("ROOM_NOT_FOUND")); requireFinished(room);
        if (!room.isPlayable()) throw new QuizEngineException("RESULTS_NOT_AVAILABLE");
        var snapshots = rounds.findByRoomIdOrderByOrderNoAsc(room.getId());
        if (snapshots.size() != room.getQuestionCount()) throw new QuizEngineException("SNAPSHOT_INVALID");
        var rating = calculator.calculate(room, snapshots, participants.findByRoomIdOrderByJoinedAtAscIdAsc(room.getId()), answers.findByRoundRoomId(room.getId()));
        for (var value : rating) if (results.findByRoomIdAndParticipantId(room.getId(), value.participant().getId()).isEmpty()) {
            results.save(new QuizResult(room, value.participant(), value.total(), value.correct(), value.score(), value.maxScore(), value.percent()));
        }
        results.flush();
    }
    /** Возвращает публичную таблицу и только собственный разбор после проверки участия и FINISHED. */
    @Transactional(readOnly = true)
    public Result read(String code, Long studentProfileId) {
        var room = rooms.findByCodeIgnoreCase(code).orElseThrow(() -> new QuizEngineException("ROOM_NOT_FOUND"));
        var viewer = participants.findByRoomIdAndStudentProfileId(room.getId(), studentProfileId).orElseThrow(() -> new QuizEngineException("PARTICIPANT_REQUIRED")); requireFinished(room);
        if (room.getKind() == QuizRoomKind.HISTORY) {
            // Исторические результаты уже рассчитаны: новые правила и время к ним не применяем.
            var saved = results.findByRoomIdOrderByScoreDescPercentDescCreatedAtAsc(room.getId());
            var leaderboard = new ArrayList<Player>();
            for (var value : saved) leaderboard.add(new Player(value.getParticipant().getId(), value.getParticipant().getDisplayName(),
                    leaderboard.size() + 1, value.getTotalQuestions(), value.getCorrectAnswers(), value.getScore(), value.getPercent(), null, value.getMaxScore()));
            var me = leaderboard.stream().filter(p -> p.participantId().equals(viewer.getId())).findFirst().orElseThrow(() -> new QuizEngineException("RESULTS_NOT_AVAILABLE"));
            return new Result(me, List.copyOf(leaderboard), List.of());
        }
        var snapshots = rounds.findByRoomIdOrderByOrderNoAsc(room.getId()); var allAnswers = answers.findByRoundRoomId(room.getId());
        if (snapshots.size() != room.getQuestionCount()) throw new QuizEngineException("SNAPSHOT_INVALID");
        var rating = calculator.calculate(room, snapshots, participants.findByRoomIdOrderByJoinedAtAscIdAsc(room.getId()), allAnswers);
        var leaderboard = rating.stream().map(v -> new Player(v.participant().getId(), v.participant().getDisplayName(), v.place(), v.total(), v.correct(), v.score(), v.percent(), v.totalResponseMs(), v.maxScore())).toList();
        var me = leaderboard.stream().filter(v -> v.participantId().equals(viewer.getId())).findFirst().orElseThrow();
        return new Result(me, leaderboard, calculator.review(room, viewer, snapshots, allAnswers));
    }
    private static void requireFinished(QuizRoom room) {
        if (room.getStatus() != QuizRoomStatus.FINISHED) throw new QuizEngineException("RESULTS_NOT_AVAILABLE");
    }
    /** Безопасная строка общего рейтинга без чужих ответов и JPA-ссылок. */
    public record Player(Long participantId, String displayName, int place, int totalRounds, int correctAnswers, BigDecimal score, int percent, Long totalResponseMs, BigDecimal maxScore) {}
    /** Общая таблица и персональный разбор; сервис не выдаёт чужую историю ответов. */
    public record Result(Player me, List<Player> leaderboard, List<QuizResultCalculator.Review> review) {}
}
