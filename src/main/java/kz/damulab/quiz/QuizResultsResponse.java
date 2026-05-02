package kz.damulab.quiz;

import java.util.List;

public record QuizResultsResponse(
        String code,
        String status,
        List<QuizParticipantResultResponse> results
) {
}
