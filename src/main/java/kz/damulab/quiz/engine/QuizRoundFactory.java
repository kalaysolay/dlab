package kz.damulab.quiz.engine;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Атомарно фиксирует набор раундов WAITING-комнаты; QuizRoomService проверяет готовность и запускает расписание в той же транзакции. */
@Service
public class QuizRoundFactory {
    private final Map<QuizGameType, QuizRoundProvider> providers;
    private final QuizRoomRepository rooms;
    private final QuizRoundRepository rounds;
    private final QuizParticipantRepository participants;
    private final QuizAudienceResolver audience;
    private final QuizGameAvailabilityPolicy availability;
    /** Подключает зависимости движка; создание компонента не читает и не изменяет БД. */
    public QuizRoundFactory(List<QuizRoundProvider> providers, QuizRoomRepository rooms, QuizRoundRepository rounds,
            QuizParticipantRepository participants, QuizAudienceResolver audience, QuizGameAvailabilityPolicy availability) {
        this.providers = providers.stream().collect(Collectors.toMap(QuizRoundProvider::gameType, Function.identity()));
        this.rooms = rooms; this.rounds = rounds; this.participants = participants; this.audience = audience; this.availability = availability;
    }
    /** Формирует полный набор внутри атомарного start; сам статус комнаты не меняет. */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<QuizRound> create(String roomCode) {
        QuizRoom room = rooms.findLockedByCode(roomCode).orElseThrow(() -> new QuizEngineException("ROOM_NOT_FOUND"));
        if (!room.isPlayable() || room.getStatus() != QuizRoomStatus.WAITING || rounds.countByRoomId(room.getId()) != 0) throw new QuizEngineException("ROUND_SET_ALREADY_CREATED");
        var v = room.getGameVersion();
        if (v != null) {
            availability.requireWaitingStart(v);
            if (room.getQuestionCount() < v.getMinQuestionCount() || room.getQuestionCount() > v.getMaxQuestionCount()
                || (!v.isQuestionCountEditable() && room.getQuestionCount() != v.getDefaultQuestionCount())
                || room.getRoundSeconds() < v.getMinRoundSeconds() || room.getRoundSeconds() > v.getMaxRoundSeconds()
                || (!v.isRoundSecondsEditable() && room.getRoundSeconds() != v.getDefaultRoundSeconds())
                || room.getMaxPlayers() != v.getMaxPlayers()
                    || !Set.of("ru", "kk").contains(room.getLanguage())) throw new QuizEngineException("SETTINGS_INVALID");
        } else {
            QuizRoomService.validateCustomSettings(room);
        }
        var required = audience.resolve(room, participants.findByRoomIdOrderByJoinedAtAscIdAsc(room.getId()));
        var provider = providers.get(room.getGameType());
        if (provider == null) throw new QuizEngineException("GAME_TYPE_UNSUPPORTED");
        var candidates = new ArrayList<>(provider.prepare(room, required.gradeIds()));
        if (candidates.stream().map(QuizRoundProvider.Source::versionId).distinct().count() != candidates.size()) throw new QuizEngineException("DUPLICATE_SOURCE");
        if (candidates.size() < room.getQuestionCount()) throw new QuizEngineException("INSUFFICIENT_PUBLISHED_CONTENT", candidates.size(), room.getQuestionCount());
        Random random = new Random(); Collections.shuffle(candidates, random); List<QuizRound> snapshots = new ArrayList<>();
        // Сначала собирается весь набор в памяти: любой malformed источник не оставляет частичных INSERT.
        for (int i = 0; i < room.getQuestionCount(); i++) snapshots.add(candidates.get(i).builder().build(i + 1, random));
        room.recordAudience(required.gradeNos());
        return List.copyOf(rounds.saveAllAndFlush(snapshots));
    }
}
