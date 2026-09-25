package kz.damulab.ai;

/** Безопасный HTML и отчёт качества, возвращаемые в редактор лекции. */
public record AiLectureGenerationResult(
        String provider,
        String model,
        String titleRu,
        String titleKk,
        String contentRu,
        String contentKk,
        AiLectureQualityReport quality
) {
}
