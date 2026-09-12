package kz.damulab.ai;

/**
 * Уровень подробности учебного разбора. Лимит остаётся в коде как техническая
 * защита расходов, а формулировка режима и стиль ответа настраиваются в промпте БД.
 */
public enum AiTranslationExplanationMode {
    ECONOMY(384),
    DETAILED(1600);

    private final int maxOutputTokens;

    AiTranslationExplanationMode(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }
}
