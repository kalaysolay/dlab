package kz.damulab.lectures;

import java.time.OffsetDateTime;
import java.util.List;

public record LectureResponse(
        Long id,
        String status,
        Long versionId,
        int versionNo,
        Long topicId,
        String topicTitleRu,
        String topicTitleKk,
        String titleRu,
        String titleKk,
        String contentRu,
        String contentKk,
        String source,
        String controlMode,
        int autoCheckpointCount,
        int attachmentCount,
        int checkpointCount,
        List<LectureAttachmentResponse> attachments,
        List<LectureCheckpointResponse> checkpoints,
        OffsetDateTime updatedAt,
        OffsetDateTime publishedAt
) {
}
