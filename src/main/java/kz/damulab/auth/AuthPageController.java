package kz.damulab.auth;

import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import kz.damulab.users.AppUser;
import kz.damulab.users.RoleCode;

@Controller
public class AuthPageController {

    private final RegistrationService registrationService;
    private final AuthenticationManager authenticationManager;

    public AuthPageController(RegistrationService registrationService, AuthenticationManager authenticationManager) {
        this.registrationService = registrationService;
        this.authenticationManager = authenticationManager;
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
        AppUser user;
        try {
            user = registrationService.register(form);
        } catch (DuplicateEmailException ex) {
            bindingResult.rejectValue("email", "duplicate", "Email уже зарегистрирован");
            return "auth/register";
        } catch (IllegalArgumentException ex) {
            bindingResult.rejectValue("role", "invalid", ex.getMessage());
            return "auth/register";
        }
        if (form.isPasskeySetupRequested()) {
            authenticateNewUser(form, request);
            return "redirect:" + profileUrl(user) + "?passkeySetup=true";
        }
        redirectAttributes.addAttribute("registered", "true");
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
