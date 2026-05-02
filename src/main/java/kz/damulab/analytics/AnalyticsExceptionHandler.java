package kz.damulab.analytics;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AnalyticsApiController.class)
public class AnalyticsExceptionHandler {

    @ExceptionHandler(AnalyticsException.class)
    ResponseEntity<Map<String, String>> handle(AnalyticsException exception) {
        HttpStatus status = switch (exception.getMessage()) {
            case "student_analytics_not_found" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of("error", exception.getMessage()));
    }
}
