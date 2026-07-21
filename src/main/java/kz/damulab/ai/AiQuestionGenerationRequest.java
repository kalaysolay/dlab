package kz.damulab.ai;

import java.util.List;

import kz.damulab.questions.QuestionType;

/**
 * Outbound-DTO запроса на генерацию вопросов, который уходит AI-провайдеру и сохраняется
 * в {@code ai_generation_jobs.request_payload_json}.
 *
 * <p>Safety boundary: здесь только предмет/класс/тема (названия, НЕ id), навык, параметры
 * генерации, инструкция методиста (после PII-очистки) и few-shot {@link #examples}. Прямые
 * идентификаторы и персональные данные сюда не попадают — это проверяется тестом
 * {@code AiContentFactoryIntegrationTest.outboundProviderDtoDoesNotContainPiiOrDirectIdentifiers}.
 *
 * <p>{@link #examples} — эталоны темы (вариант B): образцы стиля и scope. Строятся в
 * {@code AiContentFactoryService.buildRequest} и встраиваются в промпт в {@code AiPromptBuilder}.
 * Поле может быть пустым (у темы нет эталонов) или {@code null} при десериализации старых job —
 * потребители обязаны это учитывать.
 */
public record AiQuestionGenerationRequest(
        String subjectTitleRu,
        String subjectTitleKk,
        Integer gradeNo,
        String topicTitleRu,
        String topicTitleKk,
        String atomicSkillTitleRu,
        String atomicSkillTitleKk,
        QuestionType questionType,
        int difficulty,
        int count,
        AiLanguageMode languageMode,
        String methodistInstruction,
        List<AiExamplePayload> examples
) {
}
