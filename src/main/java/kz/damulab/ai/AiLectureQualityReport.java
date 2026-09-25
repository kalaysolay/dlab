package kz.damulab.ai;

import java.util.List;

/** Отчёт валидатора, который UI показывает после принятой генерации. */
public record AiLectureQualityReport(
        int score,
        int minimumScore,
        String summary,
        List<AiLectureQualityCheck> checks
) {
}
