package kz.damulab.translator;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import kz.damulab.ai.AiProviderException;

/** Преобразует ошибки переводчика в короткий JSON-контракт, удобный для мобильного UI. */
@RestControllerAdvice(assignableTypes = TranslationApiController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TranslationExceptionHandler {

    /** Ошибки длины и пустого ввода считаются некорректным запросом пользователя. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalidRequest() {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_translation_request"));
    }

    /** Недоступность модели не маскируется под ошибку ученика и допускает повтор запроса. */
    @ExceptionHandler(AiProviderException.class)
    ResponseEntity<Map<String, String>> aiUnavailable(AiProviderException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", exception.getCode()));
    }
}
