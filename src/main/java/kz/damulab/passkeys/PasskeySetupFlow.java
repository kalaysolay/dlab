package kz.damulab.passkeys;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;

/**
 * Связывает регистрацию аккаунта с одноразовым предложением настроить биометрический вход.
 * Для аккаунта с проверкой email флаг живёт только в browser-сессии и погашается после первого входа.
 */
public final class PasskeySetupFlow {

    private static final String SESSION_ATTRIBUTE = PasskeySetupFlow.class.getName() + ".PENDING";

    private PasskeySetupFlow() {
    }

    /** Запоминает, что после следующего успешного входа надо открыть настройку отпечатка. */
    public static void schedule(HttpSession session) {
        session.setAttribute(SESSION_ATTRIBUTE, Boolean.TRUE);
    }

    /**
     * Погашает отложенное предложение и возвращает URL профиля нужной роли.
     * Пустой результат означает обычный вход без перехода в настройку.
     */
    public static Optional<String> consumeRedirect(HttpServletRequest request, Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session == null || !Boolean.TRUE.equals(session.getAttribute(SESSION_ATTRIBUTE))) {
            return Optional.empty();
        }
        session.removeAttribute(SESSION_ATTRIBUTE);
        return Optional.of(profileSetupUrl(authentication));
    }

    /** Возвращает профиль STUDENT/PARENT с одноразовым параметром автоматического запуска WebAuthn. */
    public static String profileSetupUrl(Authentication authentication) {
        boolean parent = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_PARENT"));
        return (parent ? "/parent/profile" : "/student/profile") + "?passkeySetup=true";
    }
}
