package kz.damulab.analytics;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TimelineItemResponse(
        Long resultId,
        Long sessionId,
        String testType,
        String subjectTitle,
        String gradeTitle,
        int percent,
        BigDecimal score,
        BigDecimal maxScore,
        int totalQuestions,
        int correctAnswers,
        OffsetDateTime finishedAt
) {
}
