package kz.damulab.questions;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {AdminQuestionApiController.class, AdminQuestionImportApiController.class})
public class QuestionBankExceptionHandler {

    @ExceptionHandler(QuestionBankException.class)
    ResponseEntity<Map<String, String>> handleQuestionBankException(QuestionBankException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "question_not_found", "topic_not_found", "skill_not_found" -> HttpStatus.NOT_FOUND;
            case "skill_topic_mismatch", "question_archived" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of("error", ex.getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "validation_failed"));
    }
}
