package kz.damulab.gamification;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
public class StudentEngagementApiController {

    private final StudentEngagementService engagementService;

    public StudentEngagementApiController(StudentEngagementService engagementService) {
        this.engagementService = engagementService;
    }

    @GetMapping("/dashboard")
    StudentDashboardView dashboard(Principal principal) {
        return engagementService.dashboard(principal.getName());
    }
}
