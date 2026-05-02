package kz.damulab.analytics;

import java.time.OffsetDateTime;
import java.util.List;

public record AnalyticsSummaryResponse(
        Long studentId,
        String fullName,
        int testsCompleted,
        int averagePercent,
        int overallMasteryPercent,
        OffsetDateTime lastResultAt,
        List<KnowledgeMapItemResponse> knowledgeMap,
        List<LastErrorResponse> lastErrors,
        List<TimelineItemResponse> timeline,
        TrajectoryResponse trajectory,
        PredictionResponse prediction
) {
}
