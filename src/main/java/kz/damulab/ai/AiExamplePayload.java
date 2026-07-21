package kz.damulab.ai;

import kz.damulab.questions.QuestionType;

/**
 * Эталонный вопрос темы в том виде, в каком он уходит провайдеру как few-shot пример.
 *
 * <p>Осознанно «тонкий» DTO: только учебное содержимое (тело RU/KK, тип, сложность и ключ ответа
 * в JSON). Здесь НЕТ id эталона/темы, автора, внутренней заметки методиста — это часть
 * safety boundary AI Content Factory (в provider не должны попадать внутренние идентификаторы и PII).
 *
 * <p>Источник: активные ({@code include_in_ai}) {@code TopicAiExample}, отобранные и обрезанные
 * в {@code AiContentFactoryService}. Формат {@code answerKeyJson} совпадает с ключом вопроса банка
 * (варианты / пары / пропуски) — модель видит, какой ответ правильный.
 *
 * @param answerKeyJson сериализованный ключ ответа; для SCQ/MCQ — варианты, MATCHING — пары,
 *                      FILL_IN — пропуски
 */
public record AiExamplePayload(
        QuestionType questionType,
        int difficulty,
        String bodyRu,
        String bodyKk,
        String answerKeyJson
) {
}
