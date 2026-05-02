package kz.damulab.questions;

import java.time.OffsetDateTime;
import java.util.List;

public record QuestionImportJobResponse(
        Long id,
        String status,
        String sourceType,
        String originalFilename,
        int totalRows,
        int importedRows,
        int errorRows,
        OffsetDateTime createdAt,
        List<QuestionImportErrorResponse> errors
) {
}
