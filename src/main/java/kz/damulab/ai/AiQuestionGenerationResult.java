package kz.damulab.ai;

import java.util.List;

public record AiQuestionGenerationResult(
        String providerName,
        String modelName,
        List<AiGeneratedQuestionDraft> questions
) {
}
