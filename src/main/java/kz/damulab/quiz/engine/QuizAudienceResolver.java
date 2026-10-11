package kz.damulab.quiz.engine;

import java.util.*;
import java.util.stream.Collectors;
import kz.damulab.content.*;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.*;
import org.springframework.stereotype.Component;

/** Определяет классы содержания игры; класс профиля не ограничивает доступ к выбранной викторине. */
@Component
public class QuizAudienceResolver {
    private final GradeRepository grades;
    /** Подключает зависимости движка; создание компонента не читает и не изменяет БД. */
    public QuizAudienceResolver(GradeRepository grades) { this.grades = grades; }
    /** Проверяет состав комнаты и настройки содержания, возвращает ID для банков и номера для истории. */
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
        // Класс игры выбирает банк вопросов, а не допустимые классы участников.
        // Один и тот же набор доступен младшим, старшим и ученикам без класса в профиле.
        if (version.getAudiencePolicy() == AudiencePolicy.ROOM_GRADE)
            return new Audience(Set.of(selected), Set.of(room.getAudienceGradeNo()));
        if (version.getAudiencePolicy() == AudiencePolicy.FIXED_GRADES) {
            Set<Long> contentGrades = Set.copyOf(version.getGradeIds());
            Set<Integer> contentGradeNos = supported.entrySet().stream()
                    .filter(entry -> contentGrades.contains(entry.getValue())).map(Map.Entry::getKey).collect(Collectors.toSet());
            if (contentGrades.isEmpty() || contentGradeNos.size() != contentGrades.size())
                throw new QuizEngineException("AUDIENCE_INVALID");
            return new Audience(contentGrades, contentGradeNos);
        }
        // Только адаптивная стратегия PARTICIPANT_GRADES использует профили для подбора
        // общего содержания. Она не запрещает участие из-за несовпадения с классом игры.
        Set<Integer> actual = new LinkedHashSet<>();
        for (var participant : participants) {
            Integer gradeNo = participant.getStudentProfile().getGradeNo();
            if (gradeNo == null || !supported.containsKey(gradeNo)) throw new QuizEngineException("PROFILE_GRADE_REQUIRED");
            actual.add(gradeNo);
        }
        return new Audience(actual.stream().map(supported::get).collect(Collectors.toSet()), Set.copyOf(actual));
    }
    /** Согласованное пересечение классов и исторические номера, без nullable/пустой подстановки. */
    public record Audience(Set<Long> gradeIds, Set<Integer> gradeNos) {}
}
