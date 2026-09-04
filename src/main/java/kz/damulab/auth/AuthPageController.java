package kz.damulab.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import kz.damulab.users.AppUser;
import kz.damulab.users.DuplicatePhoneException;
import kz.damulab.users.InvalidPhoneException;
import kz.damulab.users.RoleCode;

@Controller
public class AuthPageController {

    private final RegistrationService registrationService;
    private final AuthenticationManager authenticationManager;
    private final EmailVerificationService emailVerificationService;

    public AuthPageController(
            RegistrationService registrationService,
            AuthenticationManager authenticationManager,
            EmailVerificationService emailVerificationService
    ) {
        this.registrationService = registrationService;
        this.authenticationManager = authenticationManager;
        this.emailVerificationService = emailVerificationService;
    }

    /** Нужен форме и при GET, и при повторном рендере после ошибки POST. */
    @ModelAttribute("emailVerificationEnabled")
    boolean emailVerificationEnabled() {
        return emailVerificationService.isEnabled();
    }

    @PostMapping("/register")
    String register(
            @Valid @ModelAttribute("registerForm") RegisterForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request
    ) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        RegistrationResult registration;
        try {
            registration = registrationService.registerWithResult(form);
        } catch (DuplicateEmailException ex) {
            bindingResult.rejectValue("email", "duplicate", "Email уже зарегистрирован");
            return "auth/register";
        } catch (DuplicatePhoneException ex) {
            bindingResult.rejectValue("phone", "duplicate", "Телефон уже зарегистрирован");
            return "auth/register";
        } catch (InvalidPhoneException ex) {
            bindingResult.rejectValue("phone", "invalid", "Введите корректный номер телефона");
            return "auth/register";
        } catch (IllegalArgumentException ex) {
            bindingResult.rejectValue("role", "invalid", ex.getMessage());
            return "auth/register";
        }
        AppUser user = registration.user();
        if (registration.verificationRequired()) {
            redirectAttributes.addAttribute(
                    registration.emailAccepted() ? "verificationSent" : "verificationSendFailed",
                    "true"
            );
            return "redirect:/login";
        }
        if (form.isPasskeySetupRequested()) {
            authenticateNewUser(form, request);
            return "redirect:" + profileUrl(user) + "?passkeySetup=true";
        }
        redirectAttributes.addAttribute("registered", "true");
        return "redirect:/login";
    }

    /** Погашает токен из письма и возвращает пользователя на страницу входа. */
    @GetMapping({"/activate-account", "/verify-email"})
    String verifyEmail(
            @RequestParam("token") String token,
            RedirectAttributes redirectAttributes,
            HttpServletResponse response
    ) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");
        EmailVerificationResult result = emailVerificationService.confirm(token);
        if (result == EmailVerificationResult.VERIFIED) {
            redirectAttributes.addAttribute("verified", "true");
        } else if (result == EmailVerificationResult.EXPIRED) {
            redirectAttributes.addAttribute("verificationExpired", "true");
        } else {
            redirectAttributes.addAttribute("verificationError", "true");
        }
        return "redirect:/login";
    }

    /**
     * Повторная отправка всегда показывает нейтральный ответ, чтобы форма не раскрывала
     * зарегистрированные адреса постороннему пользователю.
     */
    @PostMapping("/verify-email/resend")
    String resendVerificationEmail(
            @RequestParam("email") String email,
            RedirectAttributes redirectAttributes
    ) {
        emailVerificationService.resend(email);
        redirectAttributes.addAttribute("verificationResent", "true");
        return "redirect:/login";
    }

    private void authenticateNewUser(RegisterForm form, HttpServletRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(form.getEmail(), form.getPassword())
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private String profileUrl(AppUser user) {
        boolean parent = user.getRoles().stream().anyMatch(role -> role.getCode() == RoleCode.PARENT);
        return parent ? "/parent/profile" : "/student/profile";
    }
}
