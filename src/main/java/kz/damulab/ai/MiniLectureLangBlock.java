package kz.damulab.ai;

/**
 * Один язык структурированной мини-лекции (поля из JSON модели).
 */
public record MiniLectureLangBlock(
        String title,
        String theory,
        String questionAnalysis,
        String commonMistake,
        String exampleAnalysis,
        String summary
) {
}
