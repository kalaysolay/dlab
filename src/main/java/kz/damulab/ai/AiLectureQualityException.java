package kz.damulab.ai;

/**
 * Отклонение результата ниже обязательного порога. Отчёт сохраняется в исключении,
 * чтобы после исчерпания повторов API мог объяснить методисту причину отказа.
 */
public class AiLectureQualityException extends AiProviderException {

    private final AiLectureQualityReport report;

    public AiLectureQualityException(AiLectureQualityReport report) {
        super("ai_lecture_quality_below_threshold", report.summary());
        this.report = report;
    }

    public AiLectureQualityReport getReport() {
        return report;
    }
}
