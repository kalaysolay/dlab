package kz.damulab.config;

/**
 * Режим формирования набора вопросов.
 * ADAPTIVE обучает через усиление слабых навыков, RANDOM не учитывает историю ученика.
 */
public enum QuestionSelectionStrategy {
    ADAPTIVE,
    RANDOM
}
