package kz.damulab.ai;

/**
 * Контекст для генерации мини-лекции из черновика вопроса (без персональных данных ученика).
 * Предмет, класс и формулировки вопроса берутся из формы, а не из сущности «тема».
 */
public record MiniLectureGenerationRequest(
        String subjectRu,
        String subjectKk,
        String gradeTitleRu,
        String gradeTitleKk,
        int gradeNo,
        String topicTitleRu,
        String topicTitleKk,
        String questionRu,
        String questionKk,
        String optionsRu,
        String optionsKk,
        String correctAnswerRu,
        String correctAnswerKk
) {
}
