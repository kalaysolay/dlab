package kz.damulab.testing;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record TestResultResponse(
        Long resultId,
        Long sessionId,
        int totalQuestions,
        int correctAnswers,
        BigDecimal score,
        BigDecimal maxScore,
        int percent,
        OffsetDateTime createdAt,
        List<QuestionResultResponse> questions
) {
}
