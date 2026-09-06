package kz.damulab.auth;

import java.io.Serializable;
import java.util.Locale;

import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Минимальный проверенный набор Google OIDC claims, который нужен локальной регистрации.
 * Spring Security до этого шага уже проверяет подпись, issuer, audience и срок ID token.
 */
public record GoogleIdentity(String subject, String email, String fullName) implements Serializable {

    /** Извлекает identity и запрещает вход с неподтверждённым Google email. */
    public static GoogleIdentity from(OAuth2User principal) {
        String subject = stringAttribute(principal, "sub");
        String email = stringAttribute(principal, "email");
        Object verifiedClaim = principal.getAttribute("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(verifiedClaim)
                || "true".equalsIgnoreCase(String.valueOf(verifiedClaim));

        if (subject == null || email == null || !emailVerified) {
            throw new GoogleOAuthException("Google did not provide a verified email identity");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String name = stringAttribute(principal, "name");
        if (name == null) {
            name = normalizedEmail.substring(0, normalizedEmail.indexOf('@'));
        }
        return new GoogleIdentity(subject, normalizedEmail, name.trim());
    }

    private static String stringAttribute(OAuth2User principal, String name) {
        Object value = principal.getAttribute(name);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString();
    }
}
