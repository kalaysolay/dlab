package kz.damulab.passkeys;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Возвращает безопасную ошибку WebAuthn клиенту и сохраняет реальную причину в серверном журнале. */
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
        log.warn("Passkey request rejected: {}", exception.getMessage(), exception);
        return Map.of(
                "error", "passkey_request_rejected",
                "message", "Не удалось проверить данные входа на сервере"
        );
    }
}
