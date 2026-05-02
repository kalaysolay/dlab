package kz.damulab.ai;

import java.util.List;

import org.springframework.stereotype.Component;

import kz.damulab.questions.FillMatchMode;
import kz.damulab.questions.QuestionType;

@Component
public class AiDraftSchemaValidator {

    public void validate(AiGeneratedQuestionDraft draft) {
        if (draft == null) {
            throw new AiProviderException("ai_schema_invalid", "Draft is empty");
        }
        require(draft.questionType() != null, "questionType is required");
        require(notBlank(draft.bodyRu()) && notBlank(draft.bodyKk()), "RU and KK bodies are required");
        require(notBlank(draft.source()), "source is required");
        require(draft.difficulty() >= 1 && draft.difficulty() <= 5, "difficulty must be 1..5");
        require(draft.qualityScore() >= 0 && draft.qualityScore() <= 100, "qualityScore must be 0..100");
        switch (draft.questionType()) {
            case SCQ -> validateChoice(draft.options(), true);
            case MCQ -> validateChoice(draft.options(), false);
            case MATCHING -> validateMatching(draft.matchingPairs());
            case FILL_IN -> validateFill(draft.fillAnswers());
        }
    }

    private void validateChoice(List<AiGeneratedChoiceOption> options, boolean exactlyOne) {
        require(options != null && options.size() >= 2, "choice questions need at least two options");
        long correct = options.stream().filter(AiGeneratedChoiceOption::correct).count();
        require(exactlyOne ? correct == 1 : correct >= 1, "choice answer key is invalid");
        options.forEach(option -> require(notBlank(option.label()) && notBlank(option.textRu()) && notBlank(option.textKk()),
                "choice option label/RU/KK is required"));
    }

    private void validateMatching(List<AiGeneratedMatchingPair> pairs) {
        require(pairs != null && pairs.size() >= 2, "matching questions need at least two pairs");
        pairs.forEach(pair -> require(notBlank(pair.leftRu()) && notBlank(pair.leftKk())
                && notBlank(pair.rightRu()) && notBlank(pair.rightKk()), "matching pair RU/KK is required"));
    }

    private void validateFill(List<AiGeneratedFillAnswer> answers) {
        require(answers != null && !answers.isEmpty(), "fill-in questions need answers");
        answers.forEach(answer -> {
            require(notBlank(answer.placeholder()) && notBlank(answer.answer()) && answer.matchMode() != null,
                    "fill-in placeholder/answer/matchMode is required");
            if (answer.matchMode() == FillMatchMode.NUMERIC_TOLERANCE) {
                require(answer.tolerance() != null && answer.tolerance().signum() >= 0,
                        "numeric tolerance must be non-negative");
            }
        });
    }

    private void require(boolean valid, String message) {
        if (!valid) {
            throw new AiProviderException("ai_schema_invalid", message);
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
