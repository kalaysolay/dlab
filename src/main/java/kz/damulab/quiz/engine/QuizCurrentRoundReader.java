package kz.damulab.quiz.engine;

import java.time.OffsetDateTime;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import kz.damulab.quiz.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Безопасная модель чтения нового режима: только текущий раунд и факт принятия собственного ответа, без JPA-сериализации. */
@Service
public class QuizCurrentRoundReader {
    private final QuizRoomRepository rooms;
    private final QuizParticipantRepository participants;
    private final QuizRoundRepository rounds;
    private final QuizAnswerRepository answers;
    private final QuizSnapshotJson json;
    /** Подключает зависимости движка; создание компонента не читает и не изменяет БД. */
    public QuizCurrentRoundReader(QuizRoomRepository rooms, QuizParticipantRepository participants, QuizRoundRepository rounds, QuizAnswerRepository answers, QuizSnapshotJson json) {
        this.rooms = rooms; this.participants = participants; this.rounds = rounds; this.answers = answers; this.json = json;
    }
    /** Проверяет участие; выдаёт только текущее окно, архив читается без новых снимков. */
    @Transactional(readOnly = true)
    public State read(String code, Long studentProfileId, OffsetDateTime now) {
        QuizRoom room = rooms.findByCodeIgnoreCase(code).orElseThrow(() -> new QuizEngineException("ROOM_NOT_FOUND"));
        QuizParticipant participant = participants.findByRoomIdAndStudentProfileId(room.getId(), studentProfileId).orElseThrow(() -> new QuizEngineException("PARTICIPANT_REQUIRED"));
        // Архив не имеет новых снимков; завершённую комнату показываем без текущего раунда.
        CurrentRound current = null;
        if (room.isPlayable() && room.getStatus() == QuizRoomStatus.ACTIVE && room.getStartedAt() != null && !now.isBefore(room.getStartedAt())) {
            for (var round : rounds.findByRoomIdOrderByOrderNoAsc(room.getId())) {
                if (!now.isBefore(QuizAnswerEvaluator.startsAt(round)) && now.isBefore(QuizAnswerEvaluator.endsAt(round))) {
                    JsonNode config = json.read(round.getSourceConfigSnapshotJson()); Map<String, String> presentation = new LinkedHashMap<>();
                    for (String field : List.of("sourceLanguage", "targetLanguage", "pronunciation")) if (config.has(field)) presentation.put(field, config.path(field).asText());
                    current = new CurrentRound(round.getId(), round.getOrderNo(), config.path("format").asText(), round.getContentKind().name(), round.getPromptSnapshot(),
                            json.read(round.getOptionsSnapshotJson()), round.getContentLocale(), Map.copyOf(presentation),
                            QuizAnswerEvaluator.startsAt(round), QuizAnswerEvaluator.endsAt(round), answers.findByRoundIdAndParticipantId(round.getId(), participant.getId()).isPresent());
                    break;
                }
            }
        }
        return new State(room.getStatus(), room.getQuestionCount(), now, current);
    }
    /** Публичная оболочка; не содержит коллекции всех раундов. */
    public record State(QuizRoomStatus status, int totalRounds, OffsetDateTime serverTime, CurrentRound currentRound) {}
    /** Разрешённые поля текущего окна; ключи/правильность/разбор/источник отсутствуют в самом типе DTO. */
    public record CurrentRound(Long id, int orderNo, String format, String contentKind, String prompt, JsonNode options, String locale,
                               Map<String, String> presentation, OffsetDateTime startsAt, OffsetDateTime endsAt, boolean answered) {}
}
