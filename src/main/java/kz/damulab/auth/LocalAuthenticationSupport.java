package kz.damulab.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import kz.damulab.users.AppUser;

/** Создаёт обычную локальную Spring Security-сессию после уже проверенного входа или регистрации. */
@Component
public class LocalAuthenticationSupport {

    private final UserDetailsService userDetailsService;

    public LocalAuthenticationSupport(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    /** Аутентифицирует доверенного пользователя с локальными ROLE_* и сохраняет контекст в HTTP session. */
    public Authentication authenticate(AppUser user, HttpServletRequest request) {
        UserDetails details = userDetailsService.loadUserByUsername(user.getEmail());
        if (!details.isEnabled()) {
            throw new GoogleOAuthException("Local account is disabled");
        }
        // Этот вход создаётся вручную, вне UsernamePasswordAuthenticationFilter, поэтому
        // стандартная защита Spring от session fixation сама не сработает.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                details,
                null,
                details.getAuthorities()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );
        return authentication;
    }

    /** Убирает временную OAuth-аутентификацию, пока новый пользователь выбирает локальную роль. */
    public void clear(HttpServletRequest request) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        }
    }
}
