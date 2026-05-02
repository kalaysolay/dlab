package kz.damulab.auth;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthPageController {

    private final RegistrationService registrationService;

    public AuthPageController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    String register(
            @Valid @ModelAttribute("registerForm") RegisterForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        try {
            registrationService.register(form);
        } catch (DuplicateEmailException ex) {
            bindingResult.rejectValue("email", "duplicate", "Email уже зарегистрирован");
            return "auth/register";
        } catch (IllegalArgumentException ex) {
            bindingResult.rejectValue("role", "invalid", ex.getMessage());
            return "auth/register";
        }
        redirectAttributes.addAttribute("registered", "true");
        return "redirect:/login";
    }
}
