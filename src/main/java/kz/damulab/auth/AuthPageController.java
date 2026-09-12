package kz.damulab.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import kz.damulab.users.DuplicatePhoneException;
import kz.damulab.users.InvalidPhoneException;
import kz.damulab.passkeys.PasskeySetupFlow;

@Controller
public class AuthPageController {

    private final RegistrationService registrationService;
    private final EmailVerificationService emailVerificationService;
    private final LocalAuthenticationSupport localAuthentication;

    public AuthPageController(
            RegistrationService registrationService,
            EmailVerificationService emailVerificationService,
            LocalAuthenticationSupport localAuthentication
    ) {
        this.registrationService = registrationService;
        this.emailVerificationService = emailVerificationService;
        this.localAuthentication = localAuthentication;
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
        if (registration.verificationRequired()) {
            // На неподтверждённом аккаунте WebAuthn регистрировать нельзя. Запоминаем намерение
            // в этой browser-сессии и покажем системную биометрию сразу после первого входа.
            PasskeySetupFlow.schedule(request.getSession(true));
            redirectAttributes.addAttribute(
                    registration.emailAccepted() ? "verificationSent" : "verificationSendFailed",
                    "true"
            );
            return "redirect:/login";
        }
        var authentication = localAuthentication.authenticate(registration.user(), request);
        return "redirect:" + PasskeySetupFlow.profileSetupUrl(authentication);
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

}
