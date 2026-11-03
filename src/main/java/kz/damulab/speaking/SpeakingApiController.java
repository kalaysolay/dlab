package kz.damulab.speaking;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Ученический API; роль и CSRF задаёт SecurityConfig, принадлежность каждого ресурса проверяет сервис. */
@RestController
@RequestMapping("/api/student/speaking")
public class SpeakingApiController {
    private final SpeakingSessionService service;
    private final SpeakingScenarioCatalog catalog;
    private final SpeakingAccess access;
    private final SpeakingVoiceService voice;
    private final SpeakingVoiceStore voiceStore;
    public SpeakingApiController(SpeakingSessionService service, SpeakingScenarioCatalog catalog, SpeakingAccess access,
            SpeakingVoiceService voice, SpeakingVoiceStore voiceStore) {
        this.service=service; this.catalog=catalog; this.access=access;
        this.voice=voice; this.voiceStore=voiceStore;
    }
    /** Приватные ответы не должны сохраняться HTTP-кэшем или service worker. */
    @ModelAttribute
    void noStore(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    @GetMapping("/scenarios")
    List<SpeakingScenario> scenarios() { access.student(false); return catalog.list(); }
    @GetMapping("/sessions")
    List<SpeakingViews.HistoryItem> history() { return service.history(); }
    @GetMapping("/difficulties")
    List<SpeakingViews.Difficulty> difficulties() { return service.difficulties(); }
    @PostMapping("/sessions") @ResponseStatus(HttpStatus.CREATED)
    SpeakingViews.Session create(@RequestHeader("Idempotency-Key") UUID requestId,
            @Valid @RequestBody SpeakingViews.CreateRequest request) { return service.create(requestId, request); }
    @GetMapping("/sessions/{id}")
    SpeakingViews.Session get(@PathVariable UUID id) { return service.get(id); }
    /** Подтверждает введённый текст или принадлежащий ученику ASR-черновик. */
    @PostMapping("/sessions/{id}/turns") @ResponseStatus(HttpStatus.CREATED)
    SpeakingViews.Session answer(@PathVariable UUID id, @RequestHeader("Idempotency-Key") UUID requestId,
            @Valid @RequestBody SpeakingViews.AnswerRequest request) { return service.answer(id, requestId, request); }
    /** Остановка записи сразу вызывает этот маршрут; принятия учебного ответа здесь ещё нет. */
    @PostMapping("/sessions/{id}/transcriptions")
    SpeakingViews.Transcription transcribe(@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID requestId,
            @RequestParam long expectedVersion,HttpServletRequest request) throws IOException {
        return voice.upload(id,requestId,expectedVersion,request);
    }
    @DeleteMapping("/sessions/{id}/transcriptions/{attemptId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void discard(@PathVariable UUID id,@PathVariable UUID attemptId) { voiceStore.discard(id,attemptId); }
    @PostMapping("/sessions/{id}/finish")
    SpeakingViews.Session finish(@PathVariable UUID id, @Valid @RequestBody SpeakingViews.VersionRequest request) {
        return service.finish(id, request.expectedVersion());
    }
    @PostMapping("/sessions/{id}/help")
    SpeakingViews.Session help(@PathVariable UUID id, @Valid @RequestBody SpeakingViews.VersionRequest request) {
        return service.help(id, request.expectedVersion());
    }
    @PostMapping("/sessions/{id}/observations/{observationId}/dispute")
    SpeakingViews.Session dispute(@PathVariable UUID id, @PathVariable UUID observationId,
            @Valid @RequestBody SpeakingViews.VersionRequest request) {
        return service.dispute(id, observationId, request.expectedVersion());
    }
    @DeleteMapping("/sessions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) { service.delete(id); }
}
