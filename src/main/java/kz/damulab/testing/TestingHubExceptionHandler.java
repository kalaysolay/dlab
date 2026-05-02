package kz.damulab.testing;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        TestingHubApiController.class,
        TestingReferenceApiController.class
})
public class TestingHubExceptionHandler {

    @ExceptionHandler(TestingHubException.class)
    ResponseEntity<Map<String, String>> handle(TestingHubException ex) {
        HttpStatus status = switch (ex.getMessage()) {
            case "test_session_not_found", "session_question_not_found", "result_not_found" -> HttpStatus.NOT_FOUND;
            case "session_finished" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of("error", ex.getMessage()));
    }
}
