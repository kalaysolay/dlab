package kz.damulab.lectures;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AdminLectureApiController.class)
public class LectureExceptionHandler {

    @ExceptionHandler(LectureException.class)
    ResponseEntity<Map<String, String>> handleLectureException(LectureException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "lecture_not_found", "topic_not_found", "question_version_not_found", "lecture_attachment_file_not_found" ->
                    HttpStatus.NOT_FOUND;
            case "lecture_archived", "checkpoint_question_not_published", "lecture_already_archived" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of("error", ex.getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "validation_failed"));
    }
}
