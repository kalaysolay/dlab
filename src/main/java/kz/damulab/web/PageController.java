package kz.damulab.web;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;

import kz.damulab.auth.RegisterForm;
import kz.damulab.gamification.StudentEngagementService;

@Controller
public class PageController {

    private final StudentEngagementService engagementService;

    public PageController(StudentEngagementService engagementService) {
        this.engagementService = engagementService;
    }

    @GetMapping("/")
    String home() {
        return "index";
    }

    @GetMapping("/login")
    String login(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/dashboard";
        }
        return "auth/login";
    }

    @GetMapping("/register")
    String register(Model model) {
        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new RegisterForm());
        }
        return "auth/register";
    }

    @GetMapping("/dashboard")
    String dashboard(Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        if (hasRole(authentication, "ROLE_ADMIN")) {
            return "redirect:/admin";
        }
        if (hasRole(authentication, "ROLE_PARENT")) {
            return "redirect:/parent";
        }
        if (hasRole(authentication, "ROLE_STUDENT")) {
            return "redirect:/student";
        }
        return "redirect:/";
    }

    @GetMapping("/admin")
    String admin(Principal principal, Model model) {
        model.addAttribute("activeAdminNav", "dashboard");
        return "admin/dashboard";
    }

    @GetMapping("/student")
    String student(Principal principal, Model model) {
        model.addAttribute("dashboard", engagementService.dashboard(principal.getName()));
        return "student/dashboard";
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(role));
    }
}
