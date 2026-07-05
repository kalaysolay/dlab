package kz.damulab.content;

import java.time.OffsetDateTime;

public record TopicResponse(
        Long id,
        Long subjectId,
        String subjectTitleRu,
        Long gradeId,
        Integer gradeNo,
        Long parentId,
        String parentTitleRu,
        String code,
        String titleRu,
        String titleKk,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        boolean imported,
        String importNote,
        OffsetDateTime importedAt,
        long childCount,
        long skillCount
) {
}
