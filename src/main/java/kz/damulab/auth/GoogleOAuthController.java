package kz.damulab.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import kz.damulab.users.AppUser;
import kz.damulab.users.DuplicatePhoneException;
import kz.damulab.users.InvalidPhoneException;
import kz.damulab.users.RoleCode;
import kz.damulab.passkeys.PasskeySetupFlow;

/** Завершает регистрацию нового Google-пользователя локальными данными профиля. */
@Controller
public class GoogleOAuthController {

    static final String PENDING_IDENTITY_SESSION_KEY =
            GoogleOAuthController.class.getName() + ".PENDING_IDENTITY";

    private final GoogleAccountService accounts;
    private final LocalAuthenticationSupport localAuthentication;

    public GoogleOAuthController(
            GoogleAccountService accounts,
            LocalAuthenticationSupport localAuthentication
    ) {
        this.accounts = accounts;
        this.localAuthentication = localAuthentication;
    }

    @GetMapping("/register/google")
    String registration(Model model, HttpSession session) {
        GoogleIdentity identity = pendingIdentity(session);
        if (identity == null) {
            return "redirect:/login?oauthExpired";
        }
        if (!model.containsAttribute("googleRegistrationForm")) {
            GoogleRegistrationForm form = new GoogleRegistrationForm();
            form.setFullName(identity.fullName());
            model.addAttribute("googleRegistrationForm", form);
        }
        model.addAttribute("googleEmail", identity.email());
        return "auth/google-register";
    }

    @PostMapping("/register/google")
    String completeRegistration(
            @Valid @ModelAttribute("googleRegistrationForm") GoogleRegistrationForm form,
            BindingResult bindingResult,
            Model model,
            HttpServletRequest request
    ) {
        GoogleIdentity identity = pendingIdentity(request.getSession(false));
        if (identity == null) {
            return "redirect:/login?oauthExpired";
        }
        if (form.getRole() == RoleCode.ADMIN) {
            bindingResult.rejectValue("role", "google.role.invalid", "Недоступная роль");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("googleEmail", identity.email());
            return "auth/google-register";
        }

        Authentication authentication;
        try {
            AppUser user = accounts.register(identity, form);
            authentication = localAuthentication.authenticate(user, request);
        } catch (DuplicatePhoneException ex) {
            bindingResult.rejectValue("phone", "duplicate", "Телефон уже зарегистрирован");
            model.addAttribute("googleEmail", identity.email());
            return "auth/google-register";
        } catch (InvalidPhoneException ex) {
            bindingResult.rejectValue("phone", "invalid", "Введите корректный номер телефона");
            model.addAttribute("googleEmail", identity.email());
            return "auth/google-register";
        } catch (GoogleOAuthException ex) {
            request.getSession().removeAttribute(PENDING_IDENTITY_SESSION_KEY);
            localAuthentication.clear(request);
            return "redirect:/login?oauthError";
        }
        request.getSession().removeAttribute(PENDING_IDENTITY_SESSION_KEY);
        return "redirect:" + PasskeySetupFlow.profileSetupUrl(authentication);
    }

    private GoogleIdentity pendingIdentity(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(PENDING_IDENTITY_SESSION_KEY);
        return value instanceof GoogleIdentity identity ? identity : null;
    }

}
