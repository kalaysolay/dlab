package kz.damulab.questions;

import java.time.OffsetDateTime;

public record QuestionResponse(
        Long id,
        String status,
        Long currentVersionId,
        int versionNo,
        String type,
        Long topicId,
        String topicTitleRu,
        Long atomicSkillId,
        String atomicSkillTitleRu,
        int difficulty,
        String bodyRu,
        String bodyKk,
        String source,
        OffsetDateTime updatedAt,
        Integer pendingDraftVersionNo
) {
}
