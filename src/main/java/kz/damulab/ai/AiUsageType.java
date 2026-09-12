package kz.damulab.ai;

/**
 * Независимые сценарии AI, для которых администратор выбирает провайдера и модель.
 * {@link #LECTURES} сейчас используется генератором мини-лекций к вопросам, а
 * {@link #TRANSLATIONS} изолирует пользовательские переводы от контентного конвейера.
 */
public enum AiUsageType {
    QUESTIONS,
    LECTURES,
    TRANSLATIONS
}
