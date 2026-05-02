package kz.damulab.questions;

public record QuestionHealthItemResponse(
        Long questionId,
        Long currentVersionId,
        String status,
        String type,
        String topicTitleRu,
        String bodyRu,
        int attempts,
        int incorrectAnswers,
        int errorRate,
        int discriminativePower,
        int openFlagCount,
        String qualitySignal
) {
}
