package kz.damulab.lectures;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import kz.damulab.ai.AiLectureQualityException;
import kz.damulab.ai.AiProviderException;

// Прямой ученический URL черновика должен возвращать 404, как отсутствующая
// лекция, а не превращать ожидаемый lecture_not_found в серверную ошибку 500.
@RestControllerAdvice(assignableTypes = {AdminLectureApiController.class, LectureStudioApiController.class,
        StudentLecturePageController.class})
public class LectureExceptionHandler {

    /** Два одновременно создаваемых externalId разрешает уникальный индекс БД. */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> handleSyncCollision(org.springframework.dao.DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "lecture_sync_conflict"));
    }

    @ExceptionHandler(LectureException.class)
    ResponseEntity<Map<String, String>> handleLectureException(LectureException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "lecture_not_found", "topic_not_found", "question_version_not_found", "lecture_attachment_file_not_found" ->
                    HttpStatus.NOT_FOUND;
            case "lecture_archived", "checkpoint_question_not_published", "lecture_already_archived",
                    "lecture_import_external_id_duplicate", "lecture_sync_conflict" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of("error", ex.getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "validation_failed"));
    }

    /** Возвращает последний отчёт, но ни разу не отдаёт отклонённый текст лекции. */
    @ExceptionHandler(AiLectureQualityException.class)
    ResponseEntity<Map<String, Object>> handleLectureQuality(AiLectureQualityException ex) {
        return ResponseEntity.unprocessableEntity().body(Map.of(
                "error", ex.getCode(),
                "message", ex.getMessage(),
                "quality", ex.getReport()
        ));
    }

    @ExceptionHandler(AiProviderException.class)
    ResponseEntity<Map<String, String>> handleAiProvider(AiProviderException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "ai_provider_disabled", "openai_api_key_missing", "deepseek_api_key_missing" ->
                    HttpStatus.SERVICE_UNAVAILABLE;
            case "openai_request_failed", "deepseek_request_failed",
                    "openai_response_empty", "deepseek_response_empty" -> HttpStatus.BAD_GATEWAY;
            case "ai_schema_invalid" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(Map.of(
                "error", ex.getCode(),
                "message", ex.getMessage() == null ? "" : ex.getMessage()
        ));
    }
}
