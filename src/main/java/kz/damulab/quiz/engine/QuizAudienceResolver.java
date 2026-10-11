package kz.damulab.quiz.engine;

import java.util.*;
import java.util.stream.Collectors;
import kz.damulab.content.*;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.*;
import org.springframework.stereotype.Component;

/** Исполняемая аудитория из фактических профилей комнаты; пустой набор разрешён только для explicit UNIVERSAL. */
@Component
public class QuizAudienceResolver {
    private final GradeRepository grades;
    /** Подключает зависимости движка; создание компонента не читает и не изменяет БД. */
    public QuizAudienceResolver(GradeRepository grades) { this.grades = grades; }
    /** Проверяет каждого участника и выбранный класс, возвращает ID для банков и номера для истории. */
    public Audience resolve(QuizRoom room, List<QuizParticipant> participants) {
        Map<Integer, Long> supported = grades.findAllByOrderByGradeNoAsc().stream().collect(Collectors.toMap(Grade::getGradeNo, Grade::getId));
        return resolve(room, participants, supported);
    }
    /** Применяет ту же policy к пакетно загруженным классам каталога, без запроса на каждую карточку. */
    public Audience resolve(QuizRoom room, List<QuizParticipant> participants, Map<Integer, Long> supported) {
        var version = room.getGameVersion();
        if (room.getKind() == QuizRoomKind.CUSTOM_SUBJECT) {
            // Выбранный учеником класс задаёт содержание игры, а не ограничивает класс гостей.
            Long selected = supported.get(room.getAudienceGradeNo());
            if (selected == null || participants.isEmpty() || participants.size() > 4)
                throw new QuizEngineException("AUDIENCE_INVALID");
            return new Audience(Set.of(selected), Set.of(room.getAudienceGradeNo()));
        }
        if (participants.isEmpty() || participants.size() > version.getMaxPlayers()) throw new QuizEngineException("AUDIENCE_INVALID");
        Long selected = supported.get(room.getAudienceGradeNo());
        if (version.getGradeSelectionMode() != GradeSelectionMode.NONE && (selected == null || !version.getGradeIds().contains(selected))) throw new QuizEngineException("GRADE_REQUIRED");
        if (version.getAudiencePolicy() == AudiencePolicy.UNIVERSAL) return new Audience(Set.of(), Set.of());
        Set<Integer> actual = new LinkedHashSet<>();
        for (var participant : participants) {
            Integer gradeNo = participant.getStudentProfile().getGradeNo();
            if (gradeNo == null || !supported.containsKey(gradeNo)) throw new QuizEngineException("PROFILE_GRADE_REQUIRED");
            if (version.getAudiencePolicy() == AudiencePolicy.FIXED_GRADES && !version.getGradeIds().contains(supported.get(gradeNo))) throw new QuizEngineException("AUDIENCE_INVALID");
            if (version.getAudiencePolicy() == AudiencePolicy.ROOM_GRADE && gradeNo < room.getAudienceGradeNo()) throw new QuizEngineException("AUDIENCE_INVALID");
            actual.add(gradeNo);
        }
        if (version.getAudiencePolicy() == AudiencePolicy.ROOM_GRADE) return new Audience(Set.of(selected), Set.of(room.getAudienceGradeNo()));
        return new Audience(actual.stream().map(supported::get).collect(Collectors.toSet()), Set.copyOf(actual));
    }
    /** Согласованное пересечение классов и исторические номера, без nullable/пустой подстановки. */
    public record Audience(Set<Long> gradeIds, Set<Integer> gradeNos) {}
}
