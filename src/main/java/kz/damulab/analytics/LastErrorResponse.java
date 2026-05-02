package kz.damulab.analytics;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public record LastErrorResponse(
        Long sessionId,
        Long sessionQuestionId,
        String questionBody,
        String topicTitle,
        String atomicSkillTitle,
        int difficulty,
        BigDecimal pointsAwarded,
        BigDecimal maxPoints,
        Map<String, Object> studentAnswer,
        String explanation,
        OffsetDateTime evaluatedAt
) {
}
