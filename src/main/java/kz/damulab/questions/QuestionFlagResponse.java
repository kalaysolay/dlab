package kz.damulab.questions;

import java.time.OffsetDateTime;

public record QuestionFlagResponse(
        Long id,
        Long questionId,
        Long questionVersionId,
        String source,
        String status,
        String reason,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt
) {
}
