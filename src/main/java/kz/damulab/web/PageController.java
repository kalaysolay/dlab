package kz.damulab.web;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import kz.damulab.auth.EmailVerificationService;
import kz.damulab.auth.RegisterForm;
import kz.damulab.gamification.StudentEngagementService;

@Controller
public class PageController {

    private final StudentEngagementService engagementService;
    private final EmailVerificationService emailVerificationService;

    public PageController(StudentEngagementService engagementService, EmailVerificationService emailVerificationService) {
        this.engagementService = engagementService;
        this.emailVerificationService = emailVerificationService;
    }

    @GetMapping("/")
    String home() {
        return "index";
    }

    @GetMapping("/login")
    String login(Authentication authentication, @RequestParam(defaultValue = "false") boolean reauth) {
        if (!reauth && authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/dashboard";
        }
        return "auth/login";
    }

    /** Действующая доверенная сессия сразу открывает кабинет; после её истечения PWA просит биометрию. */
    @GetMapping("/app")
    String appEntry(Authentication authentication) {
        if (isAuthenticated(authentication)) {
            return "redirect:/dashboard";
        }
        return "auth/app-entry";
    }

    /** Ключ можно привязать только к текущему аутентифицированному аккаунту. */
    @GetMapping("/passkeys/setup")
    String passkeySetup(Authentication authentication, Model model) {
        model.addAttribute("passkeyUsername", authentication.getName());
        return "auth/passkey-setup";
    }

    @GetMapping("/register")
    String register(Model model) {
        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new RegisterForm());
        }
        model.addAttribute("emailVerificationEnabled", emailVerificationService.isEnabled());
        return "auth/register";
    }

    @GetMapping("/access-denied")
    String accessDenied() {
        return "error/access-denied";
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

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
