package kz.damulab.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import kz.damulab.questions.QuestionType;

import org.junit.jupiter.api.Test;

/**
 * Разбор ответа генерации вопросов: markdown fence, snake_case, пропуск questionType.
 * Покрывает путь DeepSeek (мягкий parse), не HTTP.
 */
class AiQuestionDraftParseTest {

    private final Support support = new Support(new ObjectMapper(), new AiDraftSchemaValidator());

    @Test
    void acceptsSnakeCaseQuestionType() {
        String json = """
                {
                  "questions": [{
                    "question_type": "SCQ",
                    "difficulty": 2,
                    "body_ru": "Сколько будет 2+2?",
                    "body_kk": "2+2 неше?",
                    "explanation_ru": "Сложение",
                    "explanation_kk": "Қосу",
                    "source": "test",
                    "options": [
                      {"label": "A", "text_ru": "4", "text_kk": "4", "correct": true},
                      {"label": "B", "text_ru": "5", "text_kk": "5", "correct": false}
                    ],
                    "matching_pairs": [],
                    "fill_answers": [],
                    "quality_score": 80,
                    "quality_notes": "ok",
                    "flags": []
                  }]
                }
                """;

        List<AiGeneratedQuestionDraft> drafts = support.parse(json, null);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.getFirst().questionType()).isEqualTo(QuestionType.SCQ);
        assertThat(drafts.getFirst().bodyRu()).contains("2+2");
        assertThat(drafts.getFirst().options()).hasSize(2);
    }

    @Test
    void fillsMissingQuestionTypeFromRequest() {
        String json = """
                {
                  "questions": [{
                    "difficulty": 2,
                    "bodyRu": "Сколько будет 2+2?",
                    "bodyKk": "2+2 неше?",
                    "explanationRu": "Сложение",
                    "explanationKk": "Қосу",
                    "source": "test",
                    "options": [
                      {"label": "A", "textRu": "4", "textKk": "4", "correct": true},
                      {"label": "B", "textRu": "5", "textKk": "5", "correct": false}
                    ],
                    "matchingPairs": [],
                    "fillAnswers": [],
                    "qualityScore": 80,
                    "qualityNotes": "ok",
                    "flags": []
                  }]
                }
                """;

        List<AiGeneratedQuestionDraft> drafts = support.parse(json, QuestionType.SCQ);

        assertThat(drafts.getFirst().questionType()).isEqualTo(QuestionType.SCQ);
    }

    @Test
    void rejectsMissingQuestionTypeWithoutDefault() {
        String json = """
                {
                  "questions": [{
                    "difficulty": 2,
                    "bodyRu": "RU",
                    "bodyKk": "KK",
                    "explanationRu": "e",
                    "explanationKk": "e",
                    "source": "test",
                    "options": [
                      {"label": "A", "textRu": "1", "textKk": "1", "correct": true},
                      {"label": "B", "textRu": "2", "textKk": "2", "correct": false}
                    ],
                    "matchingPairs": [],
                    "fillAnswers": [],
                    "qualityScore": 80,
                    "qualityNotes": "ok",
                    "flags": []
                  }]
                }
                """;

        assertThatThrownBy(() -> support.parse(json, null))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("questionType is required");
    }

    @Test
    void unwrapsMarkdownFence() {
        assertThat(ExternalAiProviderSupport.unwrapJsonPayload("```json\n{\"questions\":[]}\n```"))
                .isEqualTo("{\"questions\":[]}");
    }

    /** Доступ к package-private {@link ExternalAiProviderSupport#parseDrafts}. */
    private static final class Support extends ExternalAiProviderSupport {
        Support(ObjectMapper objectMapper, AiDraftSchemaValidator validator) {
            super(objectMapper, validator);
        }

        List<AiGeneratedQuestionDraft> parse(String json, QuestionType defaultType) {
            return parseDrafts(json, defaultType);
        }
    }
}
