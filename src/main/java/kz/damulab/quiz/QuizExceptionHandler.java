package kz.damulab.quiz;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Стабильные локализуемые отказы новых комнат и прежний error-контракт legacy API. */
@RestControllerAdvice(assignableTypes = QuizApiController.class)
public class QuizExceptionHandler {
    private final org.springframework.context.MessageSource messages;
    /** Подключает RU/KK-сообщения ошибок нового API. */
    public QuizExceptionHandler(org.springframework.context.MessageSource messages) { this.messages = messages; }

    /** Отображает только стабильные коды и безопасные числа, внутренние версии/SQL не выдаются. */
    @ExceptionHandler(kz.damulab.quiz.engine.QuizEngineException.class)
    ResponseEntity<Map<String, Object>> engine(kz.damulab.quiz.engine.QuizEngineException ex, java.util.Locale locale) {
        String code = QuizRoomService.externalCode(ex.getCode());
        int status = switch (code) {
            case "GAME_NOT_FOUND", "ROOM_NOT_FOUND" -> 404;
            case "ROOM_EXPIRED", "ROOM_CANCELLED" -> 410;
            case "PARTICIPANT_REQUIRED", "HOST_ONLY_ACTION", "GUEST_ONLY_ACTION" -> 403;
            case "INVALID_GRADE", "PROFILE_GRADE_REQUIRED", "SETTINGS_INVALID" -> 422;
            case "REQUEST_INVALID", "ROOM_CODE_INVALID", "ANSWER_INVALID" -> 400;
            case "ROOM_BUSY", "ROOM_CODE_EXHAUSTED" -> 503;
            default -> 409;
        };
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("code", code); body.put("message", messages.getMessage("quiz.api." + code, null, locale));
        if (ex.getAvailable() != null) body.put("details", Map.of("available", ex.getAvailable(), "required", ex.getRequired()));
        return ResponseEntity.status(status).body(body);
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> malformed(java.util.Locale locale) {
        return engine(new kz.damulab.quiz.engine.QuizEngineException("REQUEST_INVALID"), locale);
    }

    @ExceptionHandler(QuizException.class)
    ResponseEntity<Map<String, String>> quiz(QuizException ex, java.util.Locale locale) {
        // Прежний error остаётся стабильным для клиентов; перевод добавляется отдельным полем.
        String message=messages.getMessage("quiz.legacy.error."+ex.getMessage(),null,messages.getMessage("quiz.legacy.error",null,locale),locale);
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage(), "message", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException ex, java.util.Locale locale) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "validation_failed", "message", messages.getMessage("quiz.legacy.invalid",null,locale)));
    }
}
