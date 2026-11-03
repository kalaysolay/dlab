package kz.damulab.speaking;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Service;

/** Сохраняет учебные факты в speaking_observations и читает только собственную модульную статистику. */
@Service
class SpeakingFeedbackService {
    static final Set<String> SKILLS=Set.of("grammar.past_simple","grammar.present_simple","grammar.present_continuous",
            "grammar.present_perfect","grammar.future","grammar.articles","grammar.prepositions","grammar.word_order",
            "grammar.agreement","grammar.plural","grammar.comparatives","grammar.questions","grammar.negation","vocabulary.word_choice");
    private final SpeakingObservationRepository observations;
    SpeakingFeedbackService(SpeakingObservationRepository observations) { this.observations = observations; }

    /** Вызывается внутри транзакции принятия хода: без фиксации результата исправление не сохраняется. */
    void accept(SpeakingSession session, SpeakingTurn turn, List<SpeakingTutor.Feedback> feedback, OffsetDateTime now) {
        if (feedback.size() > 2) throw new IllegalStateException("Too many speaking corrections");
        for (var item : feedback) {
            // Модель выбирает только известный код; новый навык требует изменения серверного каталога.
            if (!SKILLS.contains(item.skillCode()) || item.original().isBlank() || item.original().length()>200
                    || item.corrected().isBlank() || item.corrected().length()>200 || item.explanation().isBlank() || item.explanation().length()>300
                    || !Set.of("error","style").contains(item.kind()) || !turn.learnerText.contains(item.original())) {
                throw new IllegalStateException("Invalid speaking correction");
            }
            var fact = new SpeakingObservation();
            fact.studentId = session.studentId;
            fact.sessionId = session.id;
            fact.turnId = turn.id;
            fact.skillCode = item.skillCode();
            fact.kind=item.kind();
            fact.original = item.original();
            fact.corrected = item.corrected();
            fact.explanation = item.explanation();
            fact.source = turn.source;
            fact.assisted = session.assistedNext;
            // confirmed означает проверенный вход и точное правило, а не оценку преподавателем.
            fact.evidence = "confirmed";
            fact.createdAt = now;
            fact.expiresAt = now.plusDays(180);
            observations.save(fact);
        }
    }

    /** Два независимых хода дают сигнал recurring, но не процент незнания и не школьную оценку. */
    List<SpeakingViews.Difficulty> difficulties(Long studentId, OffsetDateTime now) {
        var grouped = new TreeMap<String, List<SpeakingObservation>>();
        observations.findByStudentIdAndEvidenceAndCreatedAtGreaterThanEqualAndExpiresAtGreaterThan(
                studentId, "confirmed", now.minusDays(30), now).stream()
                .filter(o -> o.kind.equals("error") && !o.assisted && !o.source.equals("edited"))
                .forEach(o -> grouped.computeIfAbsent(o.skillCode, key -> new ArrayList<>()).add(o));
        return grouped.entrySet().stream().map(entry -> {
            var items = entry.getValue();
            long turns = items.stream().map(o -> o.turnId).distinct().count();
            return new SpeakingViews.Difficulty(entry.getKey(), turns,
                    items.stream().map(o -> o.sessionId).distinct().count(),
                    items.stream().map(o -> o.createdAt).max(Comparator.naturalOrder()).orElseThrow(),
                    turns >= 2 ? "recurring" : "observed");
        }).toList();
    }
}
