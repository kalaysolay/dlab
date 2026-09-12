package kz.damulab.ai;

/** Данные для одного перевода; языки передаются явными названиями для устойчивого промпта. */
public record AiTranslationRequest(String sourceLanguage, String targetLanguage, String text) {
}
