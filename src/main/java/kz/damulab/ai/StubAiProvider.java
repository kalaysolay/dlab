package kz.damulab.ai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import kz.damulab.questions.FillMatchMode;
import kz.damulab.questions.QuestionType;

@Component
public class StubAiProvider implements AiProvider {

    static final String FAILURE_TOKEN = "__FAIL_PROVIDER__";

    private AiQuestionGenerationRequest lastRequest;

    @Override
    public AiQuestionGenerationResult generateQuestions(AiQuestionGenerationRequest request) {
        lastRequest = request;
        if (request.methodistInstruction() != null && request.methodistInstruction().contains(FAILURE_TOKEN)) {
            throw new AiProviderException("stub_provider_failure", "Stub provider failure requested");
        }
        List<AiGeneratedQuestionDraft> drafts = new ArrayList<>();
        for (int index = 1; index <= request.count(); index++) {
            drafts.add(draft(request, index));
        }
        return new AiQuestionGenerationResult("stub", "stub-ai-content-factory-v1", drafts);
    }

    public AiQuestionGenerationRequest getLastRequest() {
        return lastRequest;
    }

    private AiGeneratedQuestionDraft draft(AiQuestionGenerationRequest request, int index) {
        String topicRu = request.topicTitleRu();
        String topicKk = request.topicTitleKk();
        int score = index % 3 == 0 ? 64 : 88;
        QuestionType type = request.questionType();
        return new AiGeneratedQuestionDraft(
                type,
                request.difficulty(),
                bodyRu(type, topicRu, index),
                bodyKk(type, topicKk, index),
                "Разделите число на 10, потому что 10% равно одной десятой.",
                "10% бір ондыққа тең, сондықтан санды 10-ға бөліңіз.",
                "AI stub: " + topicRu,
                choiceOptions(type, index),
                matchingPairs(type),
                fillAnswers(type, index),
                score,
                score >= 80 ? "Stub quality check passed" : "Draft needs methodist review",
                score >= 80 ? List.of() : List.of("low_quality_score")
        );
    }

    private String bodyRu(QuestionType type, String topicRu, int index) {
        return switch (type) {
            case SCQ, MCQ -> "AI draft " + index + ": найдите 10% от " + (100 + index * 20) + " по теме " + topicRu;
            case MATCHING -> "AI draft " + index + ": сопоставьте проценты и десятичные дроби по теме " + topicRu;
            case FILL_IN -> "AI draft " + index + ": 10% от " + (100 + index * 20) + " равно [[1]]";
        };
    }

    private String bodyKk(QuestionType type, String topicKk, int index) {
        return switch (type) {
            case SCQ, MCQ -> "AI draft " + index + ": " + topicKk + " тақырыбы бойынша " + (100 + index * 20) + " санының 10 пайызын табыңыз";
            case MATCHING -> "AI draft " + index + ": " + topicKk + " тақырыбы бойынша пайыздар мен ондық бөлшектерді сәйкестендіріңіз";
            case FILL_IN -> "AI draft " + index + ": " + (100 + index * 20) + " санының 10 пайызы [[1]]";
        };
    }

    private List<AiGeneratedChoiceOption> choiceOptions(QuestionType type, int index) {
        if (type != QuestionType.SCQ && type != QuestionType.MCQ) {
            return List.of();
        }
        boolean multi = type == QuestionType.MCQ;
        return List.of(
                new AiGeneratedChoiceOption("A", String.valueOf(10 + index * 2), String.valueOf(10 + index * 2), true),
                new AiGeneratedChoiceOption("B", String.valueOf(5 + index), String.valueOf(5 + index), multi),
                new AiGeneratedChoiceOption("C", String.valueOf(20 + index), String.valueOf(20 + index), false)
        );
    }

    private List<AiGeneratedMatchingPair> matchingPairs(QuestionType type) {
        if (type != QuestionType.MATCHING) {
            return List.of();
        }
        return List.of(
                new AiGeneratedMatchingPair("50%", "50%", "0.5", "0.5"),
                new AiGeneratedMatchingPair("25%", "25%", "0.25", "0.25")
        );
    }

    private List<AiGeneratedFillAnswer> fillAnswers(QuestionType type, int index) {
        if (type != QuestionType.FILL_IN) {
            return List.of();
        }
        return List.of(new AiGeneratedFillAnswer(
                "[[1]]",
                String.valueOf(10 + index * 2),
                FillMatchMode.NUMERIC_TOLERANCE,
                java.math.BigDecimal.valueOf(0.01)
        ));
    }
}
