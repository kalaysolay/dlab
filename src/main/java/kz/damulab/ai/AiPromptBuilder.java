package kz.damulab.ai;

import org.springframework.stereotype.Component;

@Component
public class AiPromptBuilder {

    public String systemPrompt() {
        return """
                You generate educational question drafts for Damulab.kz methodists.
                Return only valid JSON matching the provided schema.
                Do not include personal data, student names, emails, phones, raw IDs or link codes.
                Generated output is draft content for human review and must not claim to be published.
                """;
    }

    public String questionGenerationPrompt(AiQuestionGenerationRequest request) {
        return """
                Generate %d %s question drafts.
                Subject RU/KK: %s / %s.
                Grade: %d.
                Topic RU/KK: %s / %s.
                Atomic skill RU/KK: %s / %s.
                Difficulty: %d of 5.
                Language mode: %s.
                Methodist instruction: %s.
                Include qualityScore 0..100, qualityNotes, flags, source and complete answer keys.
                For SCQ exactly one option must be correct.
                For MCQ at least one option must be correct.
                For MATCHING provide at least two matchingPairs.
                For FILL_IN provide fillAnswers for placeholders like [[1]].
                """.formatted(
                request.count(),
                request.questionType().name(),
                request.subjectTitleRu(),
                request.subjectTitleKk(),
                request.gradeNo(),
                request.topicTitleRu(),
                request.topicTitleKk(),
                nullToDash(request.atomicSkillTitleRu()),
                nullToDash(request.atomicSkillTitleKk()),
                request.difficulty(),
                request.languageMode().name(),
                nullToDash(request.methodistInstruction())
        );
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
