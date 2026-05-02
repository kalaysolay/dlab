package kz.damulab.ai;

import java.time.OffsetDateTime;
import java.util.List;

public record AiGenerationJobResponse(
        Long id,
        String status,
        String providerName,
        String modelName,
        Long topicId,
        String topicTitleRu,
        Long atomicSkillId,
        String atomicSkillTitleRu,
        String questionType,
        int requestedCount,
        int difficulty,
        String languageMode,
        String instruction,
        int retryCount,
        String errorCode,
        String errorMessage,
        Long batchId,
        List<AiGeneratedQuestionItemResponse> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {
}
