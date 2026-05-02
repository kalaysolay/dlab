package kz.damulab.quiz;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import kz.damulab.testing.ChoiceDisplay;
import kz.damulab.testing.MatchingDisplay;

public record QuizRoundResponse(
        Long id,
        int orderNo,
        String type,
        String body,
        String topicTitle,
        String atomicSkillTitle,
        int difficulty,
        BigDecimal points,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        boolean timedOut,
        List<ChoiceDisplay> options,
        List<MatchingDisplay> matchingLeft,
        List<MatchingDisplay> matchingRight,
        List<String> fillPlaceholders,
        boolean answered
) {
}
