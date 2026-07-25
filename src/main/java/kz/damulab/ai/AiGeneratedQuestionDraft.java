package kz.damulab.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import kz.damulab.questions.QuestionType;

/**
 * Черновик вопроса из ответа LLM. Имена полей — camelCase (как в json_schema OpenAI).
 * {@link JsonAlias} нужны для DeepSeek и прочих провайдеров без strict schema:
 * модель часто отдаёт snake_case или укороченные ключи.
 */
public record AiGeneratedQuestionDraft(
        @JsonAlias({"question_type", "type"}) QuestionType questionType,
        int difficulty,
        @JsonAlias({"body_ru", "bodyRU"}) String bodyRu,
        @JsonAlias({"body_kk", "bodyKK", "bodyKz", "body_kz"}) String bodyKk,
        @JsonAlias({"explanation_ru", "explanationRU"}) String explanationRu,
        @JsonAlias({"explanation_kk", "explanationKK", "explanationKz", "explanation_kz"}) String explanationKk,
        String source,
        List<AiGeneratedChoiceOption> options,
        @JsonAlias({"matching_pairs"}) List<AiGeneratedMatchingPair> matchingPairs,
        @JsonAlias({"fill_answers"}) List<AiGeneratedFillAnswer> fillAnswers,
        @JsonAlias({"quality_score"}) int qualityScore,
        @JsonAlias({"quality_notes"}) String qualityNotes,
        List<String> flags
) {
}
