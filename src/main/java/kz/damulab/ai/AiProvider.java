package kz.damulab.ai;

public interface AiProvider {

    AiQuestionGenerationResult generateQuestions(AiQuestionGenerationRequest request);
}
