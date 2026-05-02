package kz.damulab.analytics;

import java.util.List;

public record TrajectoryResponse(
        int overallMasteryPercent,
        String nextFocusTitle,
        List<String> weakTopics,
        String message
) {
}
