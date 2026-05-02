package kz.damulab.testing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record QuestionResultResponse(
        Long sessionQuestionId,
        int orderNo,
        String type,
        String body,
        String topicTitle,
        String atomicSkillTitle,
        boolean correct,
        BigDecimal points,
        BigDecimal pointsAwarded,
        Object studentAnswer,
        Object correctAnswer,
        List<ChoiceDisplay> options,
        List<Map<String, String>> matchingPairs,
        String explanation
) {
}
