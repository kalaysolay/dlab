package kz.damulab.passkeys;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PasskeyException extends RuntimeException {

    public PasskeyException(String message) {
        super(message);
    }

    public PasskeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
