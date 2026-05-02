package kz.damulab.web;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import kz.damulab.gamification.AchievementView;
import kz.damulab.gamification.StudentEngagementService;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import java.util.List;

@ControllerAdvice
class NavigationModelAdvice {

    private final StudentEngagementService engagementService;

    NavigationModelAdvice(StudentEngagementService engagementService) {
        this.engagementService = engagementService;
    }

    @ModelAttribute
    void navigationState(HttpServletRequest request, Principal principal, Model model) {
        String path = request.getRequestURI();
        if (!model.containsAttribute("activeStudentNav")) {
            model.addAttribute("activeStudentNav", activeStudentNav(path));
        }
        if (!model.containsAttribute("activeParentNav")) {
            model.addAttribute("activeParentNav", activeParentNav(path));
        }
        if (path != null && path.startsWith("/student") && principal != null && !model.containsAttribute("studentAchievements")) {
            List<AchievementView> items = engagementService.dashboard(principal.getName()).achievements();
            model.addAttribute("studentAchievements", items);
        }
    }

    private String activeStudentNav(String path) {
        if (path == null) {
            return "";
        }
        if (path.startsWith("/student/tests") || path.startsWith("/student/test-sessions")) {
            return "tests";
        }
        if (path.startsWith("/student/analytics")) {
            return "analytics";
        }
        if (path.startsWith("/student/quiz")) {
            return "quiz";
        }
        if (path.startsWith("/student/profile")) {
            return "profile";
        }
        if (path.startsWith("/student/achievements")) {
            return "achievements";
        }
        if (path.equals("/student")) {
            return "home";
        }
        return "";
    }

    private String activeParentNav(String path) {
        if (path == null) {
            return "";
        }
        if (path.startsWith("/parent/profile")) {
            return "profile";
        }
        if (path.startsWith("/parent")) {
            return "home";
        }
        return "";
    }
}
