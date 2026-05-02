package kz.damulab.ai;

public record AiGeneratedQuestionItemResponse(
        Long id,
        Long batchId,
        String reviewStatus,
        String questionType,
        int difficulty,
        String bodyRu,
        String bodyKk,
        String explanationRu,
        String explanationKk,
        String source,
        String optionsJson,
        String answerKeyJson,
        int qualityScore,
        String qualityNotes,
        String flagsJson,
        Long createdQuestionId
) {
}
