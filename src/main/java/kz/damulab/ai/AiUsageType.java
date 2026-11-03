package kz.damulab.ai;

/**
 * Независимые сценарии AI, для которых администратор выбирает провайдера и модель.
 * {@link #LECTURES} сейчас используется генератором мини-лекций к вопросам, а
 * {@link #TRANSLATIONS} изолирует пользовательские переводы от контентного конвейера.
 * {@link #SPEAKING} задаёт отдельную модель собеседника и его проверок безопасности.
 */
public enum AiUsageType {
    QUESTIONS,
    LECTURES,
    TRANSLATIONS,
    SPEAKING
}
