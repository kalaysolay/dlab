package kz.damulab.ai;

/**
 * Полный предметный контекст для генерации двуязычной лекции.
 * Значения загружаются сервером по topicId, поэтому клиент не может подменить класс или предмет.
 */
public record AiLectureGenerationRequest(
        String subjectTitleRu,
        String subjectTitleKk,
        int gradeNo,
        String gradeTitleRu,
        String gradeTitleKk,
        String topicTitleRu,
        String topicTitleKk,
        String methodistInstruction
) {
}
