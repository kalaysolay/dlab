package kz.damulab;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import kz.damulab.ai.AiDraftSchemaValidator;
import kz.damulab.ai.AiGeneratedChoiceOption;
import kz.damulab.ai.AiGeneratedQuestionDraft;
import kz.damulab.ai.AiProviderException;
import kz.damulab.questions.QuestionType;

import org.junit.jupiter.api.Test;

class AiDraftSchemaValidatorTest {

    private final AiDraftSchemaValidator validator = new AiDraftSchemaValidator();

    @Test
    void rejectsScqWithoutExactlyOneCorrectAnswer() {
        AiGeneratedQuestionDraft draft = new AiGeneratedQuestionDraft(
                QuestionType.SCQ,
                2,
                "RU",
                "KK",
                "ex RU",
                "ex KK",
                "source",
                List.of(
                        new AiGeneratedChoiceOption("A", "1", "1", true),
                        new AiGeneratedChoiceOption("B", "2", "2", true)
                ),
                List.of(),
                List.of(),
                90,
                "ok",
                List.of()
        );

        assertThatThrownBy(() -> validator.validate(draft))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("choice answer key is invalid");
    }
}
