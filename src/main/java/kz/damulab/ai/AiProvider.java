package kz.damulab.ai;

public interface AiProvider {

    AiQuestionGenerationResult generateQuestions(AiQuestionGenerationRequest request);

    AiMiniLectureResult generateMiniLecture(MiniLectureGenerationRequest request);
}
