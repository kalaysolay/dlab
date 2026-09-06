package kz.damulab.auth;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import kz.damulab.users.AppUser;

/**
 * Завершает Google OIDC callback: существующий аккаунт получает локальные роли сразу,
 * а новый переходит на короткую форму выбора STUDENT/PARENT.
 */
@Component
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthSuccessHandler.class);

    private final GoogleAccountService accounts;
    private final LocalAuthenticationSupport localAuthentication;
    private final RedirectStrategy redirects = new DefaultRedirectStrategy();

    public GoogleOAuthSuccessHandler(
            GoogleAccountService accounts,
            LocalAuthenticationSupport localAuthentication
    ) {
        this.accounts = accounts;
        this.localAuthentication = localAuthentication;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken token)
                    || !"google".equals(token.getAuthorizedClientRegistrationId())
                    || !(token.getPrincipal() instanceof OAuth2User principal)) {
                throw new GoogleOAuthException("Unexpected OAuth provider");
            }

            GoogleIdentity identity = GoogleIdentity.from(principal);
            Optional<AppUser> existing = accounts.findAndLinkExisting(identity);
            if (existing.isPresent()) {
                Authentication local = localAuthentication.authenticate(existing.get(), request);
                redirects.sendRedirect(request, response, successUrl(local));
                return;
            }

            request.getSession(true).setAttribute(GoogleOAuthController.PENDING_IDENTITY_SESSION_KEY, identity);
            localAuthentication.clear(request);
            redirects.sendRedirect(request, response, "/register/google");
        } catch (GoogleOAuthException ex) {
            log.warn("Google OAuth login rejected: {}", ex.getMessage());
            localAuthentication.clear(request);
            redirects.sendRedirect(request, response, "/login?oauthError");
        }
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
}
