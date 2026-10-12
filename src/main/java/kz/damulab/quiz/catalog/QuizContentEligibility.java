package kz.damulab.quiz.catalog;

import java.util.*;

/** Общие проверки опубликованного состава для диагностики и будущего старта; черновики не участвуют. */
public final class QuizContentEligibility {
    private QuizContentEligibility() {}

    /** Проверяет явную аудиторию всех участников; пустой контекст допускает только universal. */
    public static boolean audience(boolean universal, Set<Long> allowed, Set<Long> required) {
        return universal || (!required.isEmpty() && allowed.containsAll(required));
    }

    /** Предметный банк не имеет explicit universal; правило общее для диагностики и движка. */
    public static boolean subjectAudience(kz.damulab.questions.QuestionVersion v, Set<Long> required) {
        Set<Long> allowed = new HashSet<>();
        v.getGradeLinks().forEach(g -> allowed.add(g.getGrade().getId()));
        return audience(false, allowed, required);
    }

    /** Редакционная полнота предметного вопроса; формат JSON дополнительно проверяется при сборке снимка. */
    public static boolean subjectComplete(kz.damulab.questions.QuestionVersion v) {
        return !blank(v.getBodyRu()) && !blank(v.getBodyKk()) && !blank(v.getSource()) && !blank(v.getAnswerKeyJson());
    }

    /** Проверяет редакционную полноту слова независимо от наличия дистракторов. */
    public static boolean wordComplete(VocabularyItemVersion v, List<VocabularyItemAnswer> answers) {
        return v.getReviewedAt() != null && !blank(v.getSourceText()) && !blank(v.getSourceReference())
                && (!blank(v.getMeaningNoteRu()) || !blank(v.getMeaningNoteKk()))
                && v.getDifficulty() >= 1 && v.getDifficulty() <= 5
                && (v.isUniversalAudience() != !v.getGradeIds().isEmpty())
                && answers.stream().filter(VocabularyItemAnswer::isPrimary).count() == 1
                && !answers.isEmpty() && answers.stream().noneMatch(a -> blank(a.getAnswerText()) || blank(a.getAnswerNormalized()))
                && answers.stream().map(VocabularyItemAnswer::getAnswerNormalized).distinct().count() == answers.size();
    }

    /** Требует действующую проверенную публикацию набора, включая межнаборных кандидатов. */
    public static boolean wordSetComplete(VocabularySet set) {
        var v = set.getPublishedVersion();
        return set.getStatus() != EditorialStatus.ARCHIVED && v != null && v.getStatus() == EditorialStatus.PUBLISHED
                && v.getPublishedAt() != null && v.getReviewedAt() != null && !blank(v.getSourceReference());
    }

    /** Проверяет оба текста/объяснения и источник факта; false не трактуется как пропуск. */
    public static boolean factComplete(FactStatementVersion v) {
        return v.getReviewedAt() != null && !blank(v.getTextRu()) && !blank(v.getTextKk())
                && !blank(v.getExplanationRu()) && !blank(v.getExplanationKk()) && !blank(v.getSourceReference())
                && (v.isUniversalAudience() != !v.getGradeIds().isEmpty());
    }

    /** Возвращает отсутствие обязательного текста, включая строку из пробелов. */
    public static boolean blank(String value) { return value == null || value.isBlank(); }
}
