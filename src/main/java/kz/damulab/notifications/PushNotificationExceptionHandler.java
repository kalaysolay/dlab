package kz.damulab.notifications;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AdminPushNotificationApiController.class)
public class PushNotificationExceptionHandler {

    @ExceptionHandler(PushNotificationException.class)
    ResponseEntity<Map<String, String>> handlePushNotificationException(PushNotificationException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "push_not_found", "subject_not_found" -> HttpStatus.NOT_FOUND;
            case "push_not_editable" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of("error", ex.getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "validation_failed"));
    }
}
