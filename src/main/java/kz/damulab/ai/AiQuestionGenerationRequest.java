package kz.damulab.ai;

import kz.damulab.questions.QuestionType;

public record AiQuestionGenerationRequest(
        String subjectTitleRu,
        String subjectTitleKk,
        Integer gradeNo,
        String topicTitleRu,
        String topicTitleKk,
        String atomicSkillTitleRu,
        String atomicSkillTitleKk,
        QuestionType questionType,
        int difficulty,
        int count,
        AiLanguageMode languageMode,
        String methodistInstruction
) {
}
