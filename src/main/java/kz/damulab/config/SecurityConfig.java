package kz.damulab.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Configuration
public class SecurityConfig {

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login",
                                "/register",
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
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        // Web Push: сохранение подписки браузера; только аутентифицированный STUDENT
                        .requestMatchers("/api/push/subscribe").hasRole("STUDENT")
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
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/auth/**", "/api/push/**"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler((request, response, authentication) ->
                                redirectStrategy.sendRedirect(request, response, successUrl(authentication)))
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler()))
                .build();
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
