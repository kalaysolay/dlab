package kz.damulab.analytics;

import java.security.Principal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics/student/{studentId}")
public class AnalyticsApiController {

    private final AnalyticsService analytics;

    public AnalyticsApiController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/timeline")
    List<TimelineItemResponse> timeline(Principal principal, @PathVariable Long studentId) {
        return analytics.timeline(principal, studentId);
    }

    @GetMapping("/knowledge-map")
    List<KnowledgeMapItemResponse> knowledgeMap(Principal principal, @PathVariable Long studentId) {
        return analytics.knowledgeMap(principal, studentId);
    }

    @GetMapping("/last-errors")
    List<LastErrorResponse> lastErrors(Principal principal, @PathVariable Long studentId) {
        return analytics.lastErrors(principal, studentId);
    }

    @GetMapping("/trajectory")
    TrajectoryResponse trajectory(Principal principal, @PathVariable Long studentId) {
        return analytics.trajectory(principal, studentId);
    }

    @GetMapping("/prediction")
    PredictionResponse prediction(Principal principal, @PathVariable Long studentId) {
        return analytics.prediction(principal, studentId);
    }
}
