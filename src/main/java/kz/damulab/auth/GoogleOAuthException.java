package kz.damulab.auth;

/** Ошибка проверки или привязки Google identity, безопасно отображаемая как общий сбой входа. */
public class GoogleOAuthException extends RuntimeException {

    public GoogleOAuthException(String message) {
        super(message);
    }
}
