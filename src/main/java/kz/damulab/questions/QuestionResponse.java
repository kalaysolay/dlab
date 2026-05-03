package kz.damulab.questions;

import java.time.OffsetDateTime;
import java.util.List;

public record QuestionResponse(
        Long id,
        String status,
        Long currentVersionId,
        int versionNo,
        String type,
        Long subjectId,
        List<Long> topicIds,
        Long primaryTopicId,
        String primaryTopicTitleRu,
        List<Long> gradeIds,
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
