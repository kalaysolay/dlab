package kz.damulab.translator;

/** Два поддерживаемых направления; порядок языков используется и UI, и AI-промптом. */
public enum TranslationDirection {
    KAZAKH_TO_RUSSIAN("Kazakh", "Russian"),
    RUSSIAN_TO_KAZAKH("Russian", "Kazakh");

    private final String sourceLanguage;
    private final String targetLanguage;

    TranslationDirection(String sourceLanguage, String targetLanguage) {
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
    }

    public String sourceLanguage() {
        return sourceLanguage;
    }

    public String targetLanguage() {
        return targetLanguage;
    }
}
