package kz.damulab.auth;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import kz.damulab.users.AppUser;
import kz.damulab.users.AppUserRepository;

@RestController
@RequestMapping("/api")
public class AuthApiController {

    private final RegistrationService registrationService;
    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;

    public AuthApiController(
            RegistrationService registrationService,
            AuthenticationManager authenticationManager,
            AppUserRepository users
    ) {
        this.registrationService = registrationService;
        this.authenticationManager = authenticationManager;
        this.users = users;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    CurrentUserResponse register(@Valid @RequestBody RegisterForm form) {
        return toResponse(registrationService.register(form));
    }

    @PostMapping("/auth/login")
    CurrentUserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        servletRequest.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );
        AppUser user = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        return toResponse(user);
    }

    @GetMapping("/me")
    CurrentUserResponse me(Authentication authentication) {
        AppUser user = users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        return toResponse(user);
    }

    private CurrentUserResponse toResponse(AppUser user) {
        Set<String> roles = user.getRoles().stream()
                .map(role -> role.getCode().name())
                .collect(Collectors.toUnmodifiableSet());
        return new CurrentUserResponse(user.getId(), user.getEmail(), user.getFullName(), roles);
    }
}
