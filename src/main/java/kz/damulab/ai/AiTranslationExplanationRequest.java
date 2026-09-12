package kz.damulab.ai;

/** Исходный текст и перевод, по которым LLM готовит понятный ученику разбор. */
public record AiTranslationExplanationRequest(
        String sourceLanguage,
        String targetLanguage,
        String sourceText,
        String translatedText,
        String explanationLanguage,
        AiTranslationExplanationMode explanationMode
) {
}
