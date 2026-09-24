package kz.damulab.passkeys;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PasskeyException extends RuntimeException {
    private String code = "passkey_request_rejected";
    private String userMessage = "Не удалось подтвердить ключ доступа. Повторите попытку или войдите по паролю.";

    /** Безопасные код и подсказка; техническая причина остаётся только в серверных логах. */
    public PasskeyException(String code, String userMessage) {
        super(code);
        this.code = code;
        this.userMessage = userMessage;
    }

    public String getCode() { return code; }
    public String getUserMessage() { return userMessage; }


    public PasskeyException(String message) {
        super(message);
    }

    public PasskeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
