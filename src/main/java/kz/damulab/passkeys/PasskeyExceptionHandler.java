package kz.damulab.passkeys;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Возвращает безопасную ошибку WebAuthn клиенту и сохраняет реальную причину в серверном журнале. */
// Иначе общий AuthExceptionHandler перехватывает вложенный IllegalArgumentException и теряет код диагностики.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PasskeyApiController.class)
public class PasskeyExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PasskeyExceptionHandler.class);

    /**
     * Не отправляет детали криптографической проверки в браузер, но пишет stack trace в лог:
     * без него ошибки origin/RP ID, просроченной сессии и формата attestation выглядели одинаково.
     */
    @ExceptionHandler(PasskeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> passkeyError(PasskeyException exception) {
        String reference = UUID.randomUUID().toString();
        log.warn("Passkey request rejected [{}]: {}", reference, exception.getMessage(), exception);
        return Map.of(
                "error", exception.getCode(),
                "message", exception.getUserMessage(),
                "reference", reference
        );
    }
}
