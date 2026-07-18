package kz.damulab.ai;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AdminAiApiController.class)
public class AiContentFactoryExceptionHandler {

    @ExceptionHandler(AiContentFactoryException.class)
    ResponseEntity<Map<String, String>> handle(AiContentFactoryException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "ai_job_not_found", "ai_item_not_found", "topic_not_found", "skill_not_found" -> HttpStatus.NOT_FOUND;
            case "ai_item_not_editable", "ai_item_not_approvable", "ai_item_approved_not_deletable",
                    "ai_job_retry_requires_failed_status" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of("error", ex.getCode()));
    }

    /** Явнее глобального AuthExceptionHandler: в message — имена полей, не прошедших @Valid. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String fields = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (fields.isBlank()) {
            fields = "Request validation failed";
        }
        return ResponseEntity.badRequest().body(Map.of(
                "error", "validation_error",
                "message", fields
        ));
    }
}
