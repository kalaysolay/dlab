package kz.damulab.ai;

import java.util.Set;

/**
 * Стабильные ключи шаблонов и их программный контракт. Текст и версии живут
 * в БД, а допустимые переменные остаются в коде рядом с формированием данных.
 */
public enum AiPromptCode {
    LECTURE_GENERATE(Set.of(
            "subjectTitleRu", "subjectTitleKk", "gradeNo", "gradeTitleRu", "gradeTitleKk",
            "topicTitleRu", "topicTitleKk", "methodistInstruction"
    )),
    TRANSLATION_TRANSLATE(Set.of("sourceLanguage", "targetLanguage", "textJson")),
    TRANSLATION_EXPLAIN(Set.of(
            "sourceLanguage", "targetLanguage", "explanationLanguage",
            "sourceTextJson", "translatedTextJson", "explanationMode"
    ));

    private final Set<String> requiredPlaceholders;

    AiPromptCode(Set<String> requiredPlaceholders) {
        this.requiredPlaceholders = requiredPlaceholders;
    }

    public Set<String> requiredPlaceholders() {
        return requiredPlaceholders;
    }
}
