package kz.damulab.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import kz.damulab.users.DuplicatePhoneException;
import kz.damulab.users.InvalidPhoneException;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> duplicateEmail(DuplicateEmailException exception) {
        return Map.of("error", "duplicate_email", "message", exception.getMessage());
    }

    @ExceptionHandler(DuplicatePhoneException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> duplicatePhone() {
        return Map.of("error", "duplicate_phone", "message", "Phone is already registered");
    }

    @ExceptionHandler(InvalidPhoneException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> invalidPhone() {
        return Map.of("error", "invalid_phone", "message", "Phone must use a valid international format");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> invalidRequest(IllegalArgumentException exception) {
        return Map.of("error", "invalid_request", "message", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> validationError() {
        return Map.of("error", "validation_error", "message", "Request validation failed");
    }

    @ExceptionHandler(EmailDeliveryException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    Map<String, String> emailDeliveryError() {
        return Map.of("error", "email_delivery_failed", "message", "Could not send verification email");
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    Map<String, String> authenticationError() {
        // Не различаем неверный пароль и неподтверждённый адрес в API, чтобы не раскрывать состояние аккаунта.
        return Map.of("error", "authentication_failed", "message", "Invalid email or password");
    }
}
