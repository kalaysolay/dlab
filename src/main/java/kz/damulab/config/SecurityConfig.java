package kz.damulab.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import kz.damulab.parentlink.ParentLinkInvitationPageController;

import kz.damulab.auth.GoogleOAuthAvailability;
import kz.damulab.auth.GoogleOAuthSuccessHandler;

@Configuration
public class SecurityConfig {

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            GoogleOAuthAvailability googleOAuth,
            GoogleOAuthSuccessHandler googleSuccessHandler
    ) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/parent-link-invitations/confirm").permitAll()
                        .requestMatchers(
                                "/",
                                "/login",
                                "/register",
                                "/register/google",
                                "/oauth2/authorization/google",
                                "/login/oauth2/code/google",
                                "/activate-account",
                                "/verify-email",
                                "/verify-email/resend",
                                "/css/**",
                                "/js/**",
                                "/icons/**",
                                "/fonts/**",
                                "/manifest.webmanifest",
                                "/service-worker.js",
                                "/favicon.ico",
                                "/access-denied",
                                // Офлайн-страница кэшируется SW и отдаётся без сети — должна быть публичной
                                "/offline"
                        ).permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/verification-email/resend").permitAll()
                        .requestMatchers("/api/passkeys/login/options", "/api/passkeys/login").permitAll()
                        // Web Push: сохранение подписки браузера; только аутентифицированный STUDENT
                        .requestMatchers("/api/push/subscribe", "/api/push/unsubscribe").hasRole("STUDENT")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/analytics/**").hasAnyRole("STUDENT", "PARENT")
                        .requestMatchers("/api/quiz/**").hasRole("STUDENT")
                        .requestMatchers("/api/tests/**", "/api/test-sessions/**", "/api/test-results/**").hasRole("STUDENT")
                        .requestMatchers("/api/student/**").hasRole("STUDENT")
                        .requestMatchers("/api/parent/**").hasRole("PARENT")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/student/**").hasRole("STUDENT")
                        .requestMatchers("/parent/**").hasRole("PARENT")
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/auth/**", "/api/passkeys/**", "/api/push/**"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler((request, response, authentication) ->
                                redirectStrategy.sendRedirect(
                                        request,
                                        response,
                                        hasPendingParentLinkInvitation(request)
                                                ? "/parent-link-invitations/confirm"
                                                : successUrl(authentication)
                                ))
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler()));

        // oauth2Login требует ClientRegistrationRepository. Не включаем фильтры и не показываем
        // кнопку, пока GOOGLE_CLIENT_ID/SECRET не создали registrationId=google.
        if (googleOAuth.isEnabled()) {
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login")
                    .successHandler(googleSuccessHandler)
                    .failureUrl("/login?oauthError")
            );
        }
        return http.build();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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

    /** После входа возвращает пользователя к ссылке, чей секрет уже убран в серверную сессию. */
    private boolean hasPendingParentLinkInvitation(jakarta.servlet.http.HttpServletRequest request) {
        return request.getSession(false) != null
                && request.getSession(false).getAttribute(
                        ParentLinkInvitationPageController.SESSION_TOKEN_ATTRIBUTE
                ) != null;
    }

    /**
     * HTML-запросы без нужной роли ведём на дружелюбную страницу, API — короткий 403 JSON.
     */
    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            if (request.getRequestURI().startsWith("/api/")) {
                response.setStatus(403);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"access_denied\"}");
                return;
            }
            redirectStrategy.sendRedirect(request, response, "/access-denied");
        };
    }
}
