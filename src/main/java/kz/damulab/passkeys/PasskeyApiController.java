package kz.damulab.passkeys;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Хранит одноразовые WebAuthn-запросы в сессии и создаёт вход после проверки подписи. */
@RestController
public class PasskeyApiController {

    private static final String REGISTRATION_REQUEST_SESSION_KEY = "PASSKEY_REGISTRATION_REQUEST_JSON";
    private static final String ASSERTION_REQUEST_SESSION_KEY = "PASSKEY_ASSERTION_REQUEST_JSON";

    private final PasskeyService passkeyService;
    private final UserDetailsService userDetailsService;

    public PasskeyApiController(PasskeyService passkeyService, UserDetailsService userDetailsService) {
        this.passkeyService = passkeyService;
        this.userDetailsService = userDetailsService;
    }

    @GetMapping("/api/passkeys/status")
    PasskeyStatusResponse status(Authentication authentication) {
        return new PasskeyStatusResponse(passkeyService.hasPasskey(authentication.getName()));
    }

    @PostMapping(value = "/api/passkeys/register/options", produces = "application/json")
    String registerOptions(Authentication authentication, HttpSession session) {
        var options = passkeyService.startRegistration(authentication.getName());
        try {
            // Браузерный JSON содержит publicKey, а fromJson ожидает внутренний формат toJson.
            session.setAttribute(REGISTRATION_REQUEST_SESSION_KEY, options.toJson());
            return options.toCredentialsCreateJson();
        } catch (JsonProcessingException ex) {
            throw new PasskeyException("Could not create passkey registration options", ex);
        }
    }

    @PostMapping("/api/passkeys/register")
    @ResponseStatus(HttpStatus.CREATED)
    PasskeyStatusResponse register(
            Authentication authentication,
            @RequestBody String credentialJson,
            HttpSession session
    ) {
        String requestJson = requiredSessionValue(session, REGISTRATION_REQUEST_SESSION_KEY);
        passkeyService.finishRegistration(authentication.getName(), requestJson, credentialJson);
        return new PasskeyStatusResponse(true);
    }

    @PostMapping(value = "/api/passkeys/login/options", produces = "application/json")
    String loginOptions(@RequestBody PasskeyLoginOptionsRequest request, HttpSession session) {
        var options = passkeyService.startLogin(request.username());
        try {
            // Сохраняет challenge и привязку к username/userHandle, которую браузерный JSON теряет.
            session.setAttribute(ASSERTION_REQUEST_SESSION_KEY, options.toJson());
            return options.toCredentialsGetJson();
        } catch (JsonProcessingException ex) {
            throw new PasskeyException("Could not create passkey login options", ex);
        }
    }

    @PostMapping("/api/passkeys/login")
    PasskeyLoginResponse login(
            @RequestBody String credentialJson,
            HttpSession session,
            HttpServletRequest servletRequest
    ) {
        String requestJson = requiredSessionValue(session, ASSERTION_REQUEST_SESSION_KEY);
        String username = passkeyService.finishLogin(requestJson, credentialJson);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        new AccountStatusUserDetailsChecker().check(userDetails);
        // Вне стандартного фильтра Spring защиту от session fixation выполняем вручную.
        servletRequest.changeSessionId();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        servletRequest.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );
        return new PasskeyLoginResponse(successUrl(authentication));
    }

    private String requiredSessionValue(HttpSession session, String key) {
        Object value = session.getAttribute(key);
        // Challenge одноразовый, в том числе при неверной подписи. Повтор начинается с options.
        session.removeAttribute(key);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        throw new PasskeyException("session_expired", "Время подтверждения истекло. Нажмите кнопку, чтобы повторить.");
    }

    private String successUrl(Authentication authentication) {
        if (hasRole(authentication, "ROLE_ADMIN")) {
            return "/admin";
        }
        if (hasRole(authentication, "ROLE_PARENT")) {
            return "/parent";
        }
        if (hasRole(authentication, "ROLE_STUDENT")) {
            return "/student";
        }
        return "/dashboard";
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(role));
    }

    record PasskeyStatusResponse(boolean registered) {
    }

    record PasskeyLoginOptionsRequest(String username) {
    }

    record PasskeyLoginResponse(String redirectUrl) {
    }
}
