package kz.damulab.parentlink;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ParentLinkApiController.class)
public class ParentLinkExceptionHandler {

    @ExceptionHandler(ParentLinkException.class)
    ResponseEntity<Map<String, String>> parentLinkError(ParentLinkException exception) {
        HttpStatus status = switch (exception.getMessage()) {
            case "student_not_found", "child_not_linked_to_parent", "link_code_not_found" -> HttpStatus.NOT_FOUND;
            case "link_code_not_available", "child_email_exists" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validationError() {
        return ResponseEntity.badRequest().body(Map.of("error", "validation_failed"));
    }
}
