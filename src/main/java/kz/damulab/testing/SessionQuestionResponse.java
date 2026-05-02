package kz.damulab.testing;

import java.math.BigDecimal;
import java.util.List;

public record SessionQuestionResponse(
        Long id,
        int orderNo,
        String type,
        String body,
        Long topicId,
        String topicTitle,
        Long atomicSkillId,
        String atomicSkillTitle,
        int difficulty,
        BigDecimal points,
        List<ChoiceDisplay> options,
        List<MatchingDisplay> matchingLeft,
        List<MatchingDisplay> matchingRight,
        List<String> fillPlaceholders,
        boolean answered
) {
}
