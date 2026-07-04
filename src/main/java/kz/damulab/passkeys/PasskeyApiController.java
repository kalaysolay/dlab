package kz.damulab.passkeys;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
        String requestJson = toJson(passkeyService.startRegistration(authentication.getName()));
        session.setAttribute(REGISTRATION_REQUEST_SESSION_KEY, requestJson);
        return requestJson;
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
        session.removeAttribute(REGISTRATION_REQUEST_SESSION_KEY);
        return new PasskeyStatusResponse(true);
    }

    @PostMapping(value = "/api/passkeys/login/options", produces = "application/json")
    String loginOptions(@RequestBody PasskeyLoginOptionsRequest request, HttpSession session) {
        String requestJson = toJson(passkeyService.startLogin(request.username()));
        session.setAttribute(ASSERTION_REQUEST_SESSION_KEY, requestJson);
        return requestJson;
    }

    @PostMapping("/api/passkeys/login")
    PasskeyLoginResponse login(
            @RequestBody String credentialJson,
            HttpSession session,
            HttpServletRequest servletRequest
    ) {
        String requestJson = requiredSessionValue(session, ASSERTION_REQUEST_SESSION_KEY);
        String username = passkeyService.finishLogin(requestJson, credentialJson);
        session.removeAttribute(ASSERTION_REQUEST_SESSION_KEY);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
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
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        throw new PasskeyException("Passkey session expired");
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

    private String toJson(com.yubico.webauthn.data.PublicKeyCredentialCreationOptions options) {
        try {
            return options.toCredentialsCreateJson();
        } catch (JsonProcessingException ex) {
            throw new PasskeyException("Could not create passkey registration options", ex);
        }
    }

    private String toJson(com.yubico.webauthn.AssertionRequest request) {
        try {
            return request.toCredentialsGetJson();
        } catch (JsonProcessingException ex) {
            throw new PasskeyException("Could not create passkey login options", ex);
        }
    }

    record PasskeyStatusResponse(boolean registered) {
    }

    record PasskeyLoginOptionsRequest(String username) {
    }

    record PasskeyLoginResponse(String redirectUrl) {
    }
}
