package kz.damulab.quiz;

import java.net.URI;
import java.security.Principal;
import java.util.Set;
import com.fasterxml.jackson.databind.*;
import kz.damulab.quiz.engine.QuizEngineException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Единый API самостоятельных и каталожных игр; наружу выдаёт только безопасные DTO. */
@RestController
@RequestMapping("/api/quiz/rooms")
public class QuizApiController {
    private final QuizRoomService rooms;
    private final ObjectMapper mapper;
    /** Подключает единственный движок и строгую проверку JSON-полей. */
    public QuizApiController(QuizRoomService rooms, ObjectMapper mapper) {
        this.rooms = rooms; this.mapper = mapper;
    }
    @PostMapping
    ResponseEntity<Object> create(Principal principal, @RequestBody JsonNode payload) {
        if (payload == null || !payload.isObject()) throw new QuizEngineException("REQUEST_INVALID");
        Object response; String code;
        if (payload.has("gameCode")) {
            fields(payload, Set.of("gameCode", "gradeId", "questionCount", "roundSeconds", "idempotencyKey"));
            var value = rooms.create(principal.getName(), convert(payload, QuizGameRoomRequest.Create.class)); response = value; code = value.code();
        } else {
            fields(payload, Set.of("subjectId", "gradeId", "language", "questionCount", "roundSeconds", "idempotencyKey"));
            var value = rooms.createCustom(principal.getName(), convert(payload, QuizGameRoomRequest.CustomCreate.class)); response = value; code = value.code();
        }
        return ResponseEntity.created(URI.create("/api/quiz/rooms/" + code)).body(response);
    }
    @GetMapping("/{code}")
    Object room(Principal principal, @PathVariable String code) {
        return rooms.state(code, principal.getName());
    }
    @GetMapping("/{code}/preview")
    Object preview(Principal principal, @PathVariable String code) { return rooms.preview(code, principal.getName()); }
    @PostMapping("/{code}/join")
    Object join(Principal principal, @PathVariable String code) {
        return rooms.join(code, principal.getName());
    }
    @PostMapping("/{code}/ready")
    Object ready(Principal principal, @PathVariable String code, @RequestBody(required = false) JsonNode payload) {
        fields(payload, Set.of("ready", "settingsRevision"));
        if (!payload.path("ready").isBoolean() || !payload.path("settingsRevision").isIntegralNumber() || !payload.path("settingsRevision").canConvertToLong()) throw new QuizEngineException("REQUEST_INVALID");
        return rooms.ready(code, principal.getName(), convert(payload, QuizGameRoomRequest.Ready.class));
    }
    @PostMapping("/{code}/settings")
    Object settings(Principal principal, @PathVariable String code, @RequestBody JsonNode payload) {
        fields(payload, Set.of("gradeId", "questionCount", "roundSeconds", "settingsRevision"));
        if (!payload.path("settingsRevision").isIntegralNumber() || !payload.path("settingsRevision").canConvertToLong()) throw new QuizEngineException("REQUEST_INVALID");
        return rooms.settings(code, principal.getName(), convert(payload, QuizGameRoomRequest.Settings.class));
    }
    @PostMapping("/{code}/start")
    Object start(Principal principal, @PathVariable String code) {
        return rooms.start(code, principal.getName());
    }
    @PostMapping("/{code}/answers")
    Object answer(Principal principal, @PathVariable String code, @RequestBody JsonNode payload) {
        fields(payload, Set.of("roundId", "answer", "skipped"));
        if (payload.has("skipped") && !payload.path("skipped").isBoolean()) throw new QuizEngineException("ANSWER_INVALID");
        return rooms.answer(code, principal.getName(), convert(payload, QuizGameRoomRequest.Answer.class));
    }
    @PostMapping("/{code}/leave")
    ResponseEntity<Void> leave(Principal principal, @PathVariable String code) { rooms.leave(code, principal.getName()); return ResponseEntity.noContent().build(); }
    @PostMapping("/{code}/cancel")
    Object cancel(Principal principal, @PathVariable String code) { return rooms.cancel(code, principal.getName()); }
    @GetMapping("/{code}/results")
    Object results(Principal principal, @PathVariable String code) {
        return rooms.results(code, principal.getName());
    }
    private <T> T convert(JsonNode payload, Class<T> type) {
        try { return mapper.treeToValue(payload, type); }
        catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException ex) { throw new QuizEngineException("REQUEST_INVALID"); }
    }
    private void fields(JsonNode payload, Set<String> allowed) {
        if (payload == null || !payload.isObject()) throw new QuizEngineException("REQUEST_INVALID");
        payload.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) throw new QuizEngineException("REQUEST_INVALID"); });
        for (String field : java.util.List.of("subjectId", "gradeId", "roundId", "settingsRevision", "questionCount", "roundSeconds"))
            if (payload.hasNonNull(field) && (!payload.path(field).isIntegralNumber() || !payload.path(field).canConvertToLong())) throw new QuizEngineException("REQUEST_INVALID");
        for (String field : java.util.List.of("gameCode", "idempotencyKey", "language"))
            if (payload.hasNonNull(field) && !payload.path(field).isTextual()) throw new QuizEngineException("REQUEST_INVALID");
    }
}
