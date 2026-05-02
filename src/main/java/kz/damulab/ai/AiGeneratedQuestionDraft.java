package kz.damulab.ai;

import java.util.List;

import kz.damulab.questions.QuestionType;

public record AiGeneratedQuestionDraft(
        QuestionType questionType,
        int difficulty,
        String bodyRu,
        String bodyKk,
        String explanationRu,
        String explanationKk,
        String source,
        List<AiGeneratedChoiceOption> options,
        List<AiGeneratedMatchingPair> matchingPairs,
        List<AiGeneratedFillAnswer> fillAnswers,
        int qualityScore,
        String qualityNotes,
        List<String> flags
) {
}
