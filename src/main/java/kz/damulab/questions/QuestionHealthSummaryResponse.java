package kz.damulab.questions;

import java.util.List;

public record QuestionHealthSummaryResponse(
        int totalQuestions,
        int withAttempts,
        int highErrorQuestions,
        int noAttemptQuestions,
        int needsReviewQuestions,
        int flaggedQuestions,
        int weakDiscriminationQuestions,
        List<QuestionHealthItemResponse> items
) {
}
