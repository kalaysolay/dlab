package kz.damulab.speaking;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** Возвращает только стабильные коды ошибок: ученические фразы не попадают в ответы об исключениях и логи. */
@RestControllerAdvice(assignableTypes=SpeakingApiController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SpeakingExceptionHandler {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(SpeakingExceptionHandler.class);
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> status(ResponseStatusException exception) {
        String code=exception.getReason();
        // Только стабильный код, без URI, ID ученика, речи или тела ответа провайдера.
        // Ожидаемые отказы раньше вообще не попадали в журнал, что скрывало причину при диагностике.
        if(exception.getStatusCode().value()>=429){
            String safe=code!=null&&code.matches("speaking_[a-z_]{1,80}")?code:"speaking_error";
            log.warn("Speaking request rejected: status={} code={}",exception.getStatusCode().value(),safe);
        }
        return ResponseEntity.status(exception.getStatusCode()).header("Cache-Control","no-store")
                .body(Map.of("code", exception.getReason() == null ? "speaking_error" : exception.getReason()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String,String>> invalid() {
        return ResponseEntity.badRequest().header("Cache-Control","no-store").body(Map.of("code","speaking_invalid_request"));
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Map<String,String>> conflict() {
        return ResponseEntity.status(409).header("Cache-Control","no-store").body(Map.of("code","speaking_version_conflict"));
    }
    /** Невалидный результат провайдера не показывается ученику и не сохраняется частично. */
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String,String>> invalidProvider() {
        return ResponseEntity.status(503).header("Cache-Control","no-store")
                .body(Map.of("code","speaking_invalid_provider_response"));
    }
}
