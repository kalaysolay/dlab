package kz.damulab.questions;

public record QuestionImportErrorResponse(
        int rowNo,
        String errorCode,
        String message
) {
}
