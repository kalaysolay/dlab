package kz.damulab.analytics;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StudentAnalyticsPageController {

    private final AnalyticsService analytics;

    public StudentAnalyticsPageController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/student/analytics")
    String analytics(Principal principal, Model model) {
        model.addAttribute("analytics", analytics.currentStudentSummary(principal));
        return "student/analytics";
    }
}
