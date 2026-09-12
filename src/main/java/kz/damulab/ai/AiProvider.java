package kz.damulab.ai;

public interface AiProvider {

    AiQuestionGenerationResult generateQuestions(AiQuestionGenerationRequest request);

    AiMiniLectureResult generateMiniLecture(MiniLectureGenerationRequest request);

    /** Переводит пользовательский текст без сохранения его в приложении. */
    AiTextResult translate(AiTranslationRequest request);

    /** Объясняет уже выполненный перевод в учебном формате. */
    AiTextResult explainTranslation(AiTranslationExplanationRequest request);
}
