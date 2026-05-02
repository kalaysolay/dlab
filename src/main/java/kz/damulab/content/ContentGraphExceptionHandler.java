package kz.damulab.content;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        AdminContentApiController.class,
        ContentReferenceApiController.class
})
public class ContentGraphExceptionHandler {

    @ExceptionHandler(ContentGraphException.class)
    ResponseEntity<Map<String, String>> handleContentGraphException(ContentGraphException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "subject_not_found", "grade_not_found", "topic_not_found", "skill_not_found" -> HttpStatus.NOT_FOUND;
            case "topic_duplicate", "topic_has_children", "topic_has_skills", "topic_parent_cycle",
                    "topic_parent_scope_mismatch", "topic_has_questions", "topic_has_lectures", "skill_duplicate",
                    "skill_topic_mismatch", "skill_has_questions", "skill_has_lectures" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of("error", ex.getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "validation_failed"));
    }
}
