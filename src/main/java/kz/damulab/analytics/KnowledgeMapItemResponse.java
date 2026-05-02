package kz.damulab.analytics;

import java.time.OffsetDateTime;

public record KnowledgeMapItemResponse(
        Long topicId,
        String topicTitle,
        Long atomicSkillId,
        String atomicSkillTitle,
        int masteryPercent,
        int attempts,
        int correctAnswers,
        int totalQuestions,
        String status,
        OffsetDateTime updatedAt
) {
}
