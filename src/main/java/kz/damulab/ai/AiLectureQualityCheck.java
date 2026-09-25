package kz.damulab.ai;

/** Один понятный методисту пункт серверной проверки с вкладом в итоговую оценку. */
public record AiLectureQualityCheck(
        String code,
        String title,
        boolean passed,
        int points,
        String details
) {
}
