package kz.damulab.speaking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Оркестратор первого рабочего среза: только локальный stub, атомарное сохранение, доступ владельца и повторы. */
@Service
@Transactional
public class SpeakingSessionService {
    private final SpeakingAccess access;
    private final SpeakingScenarioCatalog catalog;
    private final SpeakingSessionRepository sessions;
    private final SpeakingTurnRepository turns;
    private final SpeakingObservationRepository observations;
    private final SpeakingFeedbackService feedback;
    private final SpeakingTutor tutor;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final SpeakingDailyUsageRepository usage;
    private final SpeakingVoiceAttemptRepository voiceAttempts;
    private final SpeakingVoiceStore voiceStore;
    private final SpeakingTranscriber transcriber;
    public SpeakingSessionService(SpeakingAccess access, SpeakingScenarioCatalog catalog,
            SpeakingSessionRepository sessions, SpeakingTurnRepository turns,
            SpeakingObservationRepository observations, SpeakingFeedbackService feedback,
            SpeakingTutor tutor, ObjectMapper mapper, Clock clock, SpeakingDailyUsageRepository usage,
            SpeakingVoiceAttemptRepository voiceAttempts,SpeakingVoiceStore voiceStore,SpeakingTranscriber transcriber) {
        this.access=access; this.catalog=catalog; this.sessions=sessions; this.turns=turns;
        this.observations=observations; this.feedback=feedback; this.tutor=tutor; this.mapper=mapper; this.clock=clock; this.usage=usage;
        this.voiceAttempts=voiceAttempts; this.voiceStore=voiceStore; this.transcriber=transcriber;
    }

    /** Создаёт сессию с первой репликой ИИ без входа ученика; повтор ключа не создаёт вторую запись. */
    public SpeakingViews.Session create(UUID requestId, SpeakingViews.CreateRequest request) {
        Long student = access.student(true).getId();
        var existing = sessions.findByStudentIdAndCreateRequestId(student, requestId);
        if (existing.isPresent()) {
            var old = existing.get();
            if (!old.scenarioCode.equals(request.scenarioCode()) || !old.level.equals(request.level())
                    || !old.language.equals(request.explanationLanguage())) throw conflict("speaking_request_reused");
            expire(old);
            return view(old);
        }
        sessions.findByStudentIdAndActiveSlot(student, 1).ifPresent(active -> {
            expire(active);
            if (active.activeSlot != null) throw conflict("speaking_active_session_exists");
        });
        // День квоты привязан к часовому поясу продукта, а не JVM или браузеру ученика.
        var day = LocalDate.now(clock.withZone(ZoneId.of("Asia/Qyzylorda")));
        var daily = usage.findByStudentIdAndDay(student, day).orElseGet(() -> {
            var value = new SpeakingDailyUsage(); value.studentId=student; value.day=day; return value;
        });
        if (daily.sessionsStarted >= 3) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "speaking_daily_limit");
        }
        var scenario = catalog.find(request.scenarioCode());
        daily.sessionsStarted++;
        usage.save(daily);
        var session = new SpeakingSession();
        session.studentId=student; session.createRequestId=requestId; session.scenarioCode=scenario.code();
        session.scenarioSnapshot=serialize(scenario); session.level=request.level(); session.language=request.explanationLanguage();
        session.createdAt=now(); session.updatedAt=session.createdAt;
        return view(sessions.saveAndFlush(session));
    }

    /** Возвращает всю беседу, включая начальный вопрос и все последующие реплики. */
    public SpeakingViews.Session get(UUID id) {
        var session = owned(id, false);
        expire(session);
        return view(session);
    }

    /** Принимает подтверждённый текст; ход и наблюдения фиксируются вместе, без внешнего запроса в транзакции. */
    public SpeakingViews.Session answer(UUID id, UUID requestId, SpeakingViews.AnswerRequest request) {
        var session = owned(id, true);
        String text=request.text().strip();
        var existing=turns.findBySessionIdAndRequestId(id, requestId);
        if (existing.isPresent()) {
            if (!existing.get().learnerText.equals(text) || !Objects.equals(existing.get().voiceAttemptId,request.voiceAttemptId()))
                throw conflict("speaking_request_reused");
            return view(session);
        }
        requireVersion(session, request.expectedVersion());
        requireActive(session);
        SpeakingVoiceAttempt voice=null;
        if(request.voiceAttemptId()!=null) {
            voice=voiceAttempts.findByIdAndStudentIdAndSessionId(request.voiceAttemptId(),session.studentId,id)
                    .orElseThrow(SpeakingSessionService::missing);
            voiceStore.requireReady(voice);
            if(voice.sessionVersion!=session.revision) throw conflict("speaking_version_conflict");
        }
        var scenario=snapshot(session);
        // Только stub: реальный провайдер потребует отдельного этапа с lease и вызовом вне этой транзакции.
        var reply=tutor.answer(scenario, session.acceptedTurns+1, text, session.language);
        var turn=new SpeakingTurn();
        turn.sessionId=id; turn.requestId=requestId; turn.sequenceNo=session.acceptedTurns+1;
        if(voice!=null) {
            // Происхождение определяет сервер по исходной ASR-расшифровке, а не флагу от клиента.
            turn.source=text.equals(voice.transcript)?"voice":"edited";
            turn.voiceAttemptId=voice.id; turn.audioSeconds=voice.audioSeconds;
            voice.close("CONSUMED"); voice.transcript=null;
        }
        turn.learnerText=text; turn.reactionText=reply.reaction(); turn.questionText=reply.question(); turn.createdAt=now();
        turn=turns.saveAndFlush(turn);
        feedback.accept(session, turn, reply.feedback(), turn.createdAt);
        session.assistedNext=false;
        session.acceptedTurns++;
        session.updatedAt=turn.createdAt;
        if (session.acceptedTurns >= scenario.questions().size()) session.close("COMPLETED", now());
        sessions.flush();
        return view(session);
    }

    /** Завершает беседу раньше; уже закрытая сессия возвращается без повторных изменений. */
    public SpeakingViews.Session finish(UUID id, long expectedVersion) {
        var session=owned(id, true);
        if (session.status.equals("ACTIVE")) {
            requireVersion(session, expectedVersion);
            session.close("ENDED", now());
            sessions.flush();
        }
        return view(session);
    }

    /** Помечает помощь на сервере: следующий ответ можно исправить, но нельзя считать независимым доказательством. */
    public SpeakingViews.Session help(UUID id, long expectedVersion) {
        var session=owned(id, true);
        requireActive(session);
        if (!session.assistedNext) {
            requireVersion(session, expectedVersion);
            session.assistedNext=true; session.updatedAt=now(); sessions.flush();
        }
        return view(session);
    }

    /** Оспоренное замечание немедленно исключается из вычисляемой сводки трудностей. */
    public SpeakingViews.Session dispute(UUID sessionId, UUID observationId, long expectedVersion) {
        var session=owned(sessionId, true);
        requireVersion(session, expectedVersion);
        var observation=observations.findByIdAndStudentIdAndSessionId(observationId, session.studentId, sessionId)
                .orElseThrow(() -> missing());
        observation.evidence="disputed";
        session.updatedAt=now();
        sessions.flush();
        return view(session);
    }

    /** Удаляет свою сессию; БД каскадно удаляет ходы и наблюдения, поэтому статистика не хранит удалённые цитаты. */
    public void delete(UUID id) { sessions.delete(owned(id, true)); sessions.flush(); }

    /** История ограничена последними 20 сессиями; идентификатор ученика никогда не принимается от клиента. */
    @Transactional(readOnly=true)
    public List<SpeakingViews.HistoryItem> history() {
        Long student=access.student(false).getId();
        return sessions.findTop20ByStudentIdOrderByCreatedAtDesc(student).stream()
                .map(s -> new SpeakingViews.HistoryItem(s.id, snapshot(s).title(s.language), s.status, s.acceptedTurns, s.createdAt)).toList();
    }

    /** Собственная сводка разговорника без записи в школьную skill_mastery. */
    @Transactional(readOnly=true)
    public List<SpeakingViews.Difficulty> difficulties() { return feedback.difficulties(access.student(false).getId(), now()); }

    private SpeakingSession owned(UUID id, boolean lockOwner) {
        Long student=access.student(lockOwner).getId();
        return sessions.findByIdAndStudentId(id, student).orElseThrow(SpeakingSessionService::missing);
    }
    private void requireVersion(SpeakingSession session, long version) {
        if (session.revision != version) throw conflict("speaking_version_conflict");
    }
    private void requireActive(SpeakingSession session) {
        if (!session.status.equals("ACTIVE") || session.updatedAt.plusMinutes(30).isBefore(now())) {
            throw conflict("speaking_session_closed");
        }
    }
    private void expire(SpeakingSession session) {
        if (session.status.equals("ACTIVE") && session.updatedAt.plusMinutes(30).isBefore(now())) {
            session.close("EXPIRED", now()); sessions.flush();
        }
    }
    private SpeakingViews.Session view(SpeakingSession session) {
        var scenario=snapshot(session);
        var history=turns.findBySessionIdOrderBySequenceNo(session.id).stream().map(t ->
                new SpeakingViews.Turn(t.id,t.sequenceNo,t.source,t.learnerText,t.reactionText,t.questionText,t.createdAt)).toList();
        var notes=observations.findBySessionIdOrderByCreatedAt(session.id).stream().map(o ->
                new SpeakingViews.Observation(o.id,o.turnId,o.skillCode,o.original,o.corrected,o.explanation,o.kind,o.evidence,o.source,o.assisted)).toList();
        return new SpeakingViews.Session(session.id,session.status,session.revision,scenario,session.level,session.language,
                scenario.opening(),scenario.firstQuestions().get(session.level),session.acceptedTurns,"STUB",transcriber.available(),history,notes);
    }
    private SpeakingScenario snapshot(SpeakingSession session) {
        try { return mapper.readValue(session.scenarioSnapshot, SpeakingScenario.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Invalid speaking snapshot", ex); }
    }
    private String serialize(SpeakingScenario scenario) {
        try { return mapper.writeValueAsString(scenario); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot snapshot speaking scenario", ex); }
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"speaking_not_found"); }
    private static ResponseStatusException conflict(String code) { return new ResponseStatusException(HttpStatus.CONFLICT,code); }
}
