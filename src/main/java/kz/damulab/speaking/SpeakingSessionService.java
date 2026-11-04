package kz.damulab.speaking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Транзакционные этапы беседы. Локальный stub выполняется здесь, внешний диалог — между beginLive и completeLive. */
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
    private final SpeakingGenerationRepository generations;
    private final SpeakingCloudProperties cloud;
    private final SpeakingLlmClient llm;
    public SpeakingSessionService(SpeakingAccess access, SpeakingScenarioCatalog catalog,
            SpeakingSessionRepository sessions, SpeakingTurnRepository turns,
            SpeakingObservationRepository observations, SpeakingFeedbackService feedback,
            SpeakingTutor tutor, ObjectMapper mapper, Clock clock, SpeakingDailyUsageRepository usage,
            SpeakingVoiceAttemptRepository voiceAttempts,SpeakingVoiceStore voiceStore,SpeakingTranscriber transcriber,
            SpeakingGenerationRepository generations,SpeakingCloudProperties cloud,SpeakingLlmClient llm) {
        this.access=access; this.catalog=catalog; this.sessions=sessions; this.turns=turns;
        this.observations=observations; this.feedback=feedback; this.tutor=tutor; this.mapper=mapper; this.clock=clock; this.usage=usage;
        this.voiceAttempts=voiceAttempts; this.voiceStore=voiceStore; this.transcriber=transcriber;
        this.generations=generations;this.cloud=cloud;this.llm=llm;
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
        // Число стартов учитываем для статистики, без дневного ограничения на количество разговоров.
        var day = LocalDate.now(clock.withZone(ZoneId.of("Asia/Qyzylorda")));
        var daily = usage.findByStudentIdAndDay(student, day).orElseGet(() -> {
            var value = new SpeakingDailyUsage(); value.studentId=student; value.day=day; return value;
        });
        var scenario = catalog.find(request.scenarioCode());
        daily.sessionsStarted++;
        usage.save(daily);
        var session = new SpeakingSession();
        if(cloud.isEnabled()){llm.selection();session.providerVersion=LiveSpeakingTutor.REVISION;}
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
        // Эта ветка только для stub-сессий; сетевой провайдер вызывается SpeakingConversationService вне транзакции.
        var reply=tutor.answer(snapshot(session), session.acceptedTurns+1, text, session.language);
        return saveAnswer(session,requestId,request,reply,hash(request),null);
    }

    /** Фиксирует лишь проверенный ответ. Для неподходящего входа сохраняется категория, без цитаты ребёнка. */
    private SpeakingViews.Session saveAnswer(SpeakingSession session,UUID requestId,SpeakingViews.AnswerRequest request,SpeakingTutor.Reply reply,String hash,SpeakingGeneration generation){
        UUID id=session.id;String text=request.text().strip();
        SpeakingVoiceAttempt voice=voice(request,session);
        var scenario=snapshot(session);
        boolean normal="NORMAL".equals(reply.disposition());
        if(!Set.of("NORMAL","REDIRECT","UNSAFE","SUPPORT","PRIVATE").contains(reply.disposition()))throw SpeakingLlmClient.unavailable();
        var turn=new SpeakingTurn();
        turn.sessionId=id; turn.requestId=requestId; turn.sequenceNo=++session.totalTurns;
        turn.disposition=reply.disposition();turn.requestHash=hash;
        // Аудит фиксирует снимок маршрута до сети. Смена модели в админке не переписывает прошлую проверку.
        turn.providerCode=generation==null?"STUB":generation.providerCode;
        turn.modelName=generation==null?"stub-v1":generation.modelName;
        turn.promptVersion=generation==null?"stub-v1":generation.promptVersion;
        if(voice!=null) {
            // Происхождение определяет сервер по исходной ASR-расшифровке, а не флагу от клиента.
            turn.source=text.equals(voice.transcript)?"voice":"edited";
            turn.voiceAttemptId=voice.id; turn.audioSeconds=voice.audioSeconds;
            voice.close("CONSUMED"); voice.transcript=null;
        }
        turn.learnerText=normal?text:""; turn.reactionText=reply.reaction();
        turn.questionText=normal&&session.acceptedTurns+1>=scenario.questions().size()?"":reply.question(); turn.createdAt=now();
        turn=turns.saveAndFlush(turn);
        if(normal)feedback.accept(session, turn, reply.feedback(), turn.createdAt);
        // Возврат к тому же вопросу не отменяет уже показанную подсказку: следующий содержательный ответ всё ещё assisted.
        if(normal)session.assistedNext=false;
        if(normal){session.acceptedTurns++;session.offTopicCount=0;}
        else if(reply.disposition().equals("REDIRECT"))session.offTopicCount++;
        session.updatedAt=turn.createdAt;
        if(reply.disposition().equals("SUPPORT"))session.close("PAUSED_SUPPORT",now());
        else if(session.acceptedTurns>=scenario.questions().size()||session.totalTurns>=12)session.close("COMPLETED",now());
        sessions.flush();
        return view(session);
    }

    @Transactional(readOnly=true)
    public boolean live(UUID id){return !owned(id,false).providerVersion.equals("stub-v1");}

    /** Резервирует три ограниченных LLM-вызова и lease; содержит только безопасную историю без идентификаторов. */
    public Prepared beginLive(UUID id,UUID requestId,SpeakingViews.AnswerRequest request){
        var session=owned(id,true);String hash=hash(request);
        var accepted=turns.findBySessionIdAndRequestId(id,requestId);
        if(accepted.isPresent()){
            if(!hash.equals(accepted.get().requestHash))throw conflict("speaking_request_reused");
            return new Prepared(null,null,null,view(session));
        }
        requireVersion(session,request.expectedVersion());requireActive(session);voice(request,session);
        var route=llm.selection();
        var previous=generations.findBySessionIdAndRequestId(id,requestId);
        if(previous.isPresent()&&!previous.get().requestHash.equals(hash))throw conflict("speaking_request_reused");
        generations.findByStudentIdAndActiveSlot(session.studentId,1).ifPresent(old->{
            if(old.expiresAt.isAfter(now()))throw conflict("speaking_generation_processing");
            old.close("FAILED");generations.flush();
        });
        var daily=daily(session.studentId);
        if(daily.llmCalls+3>72)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"speaking_llm_daily_limit");
        daily.llmCalls+=3;usage.save(daily);
        // У повторной обработки новый lease ID: поздний finally прежней попытки не закроет новый запрос.
        if(previous.isPresent()){generations.delete(previous.get());generations.flush();}
        var lease=new SpeakingGeneration();
        lease.studentId=session.studentId;lease.sessionId=id;lease.requestId=requestId;lease.requestHash=hash;
        lease.sessionVersion=session.revision;lease.createdAt=now();lease.expiresAt=now().plusMinutes(3);lease.state="PROCESSING";lease.activeSlot=1;
        lease.providerCode=route.provider().name();lease.modelName=route.model();lease.promptVersion=LiveSpeakingTutor.REVISION;
        generations.saveAndFlush(lease);
        var history=turns.findBySessionIdOrderBySequenceNo(id).stream().filter(t->t.disposition.equals("NORMAL")&&!t.learnerText.isBlank()).toList();
        var safeHistory=history.stream().skip(Math.max(0,history.size()-4)).map(t->Map.of("learner",t.learnerText,"assistant",t.reactionText+" "+t.questionText)).toList();
        String question=turns.findBySessionIdOrderBySequenceNo(id).stream().reduce((a,b)->b).map(t->t.questionText)
                .orElse(snapshot(session).firstQuestions().get(session.level));
        var focus=feedback.focus(session.studentId,session.scenarioCode,session.id,now());
        var context=new LiveSpeakingTutor.Context(snapshot(session),session.level,session.language,question,session.acceptedTurns,safeHistory,request.text().strip(),focus);
        return new Prepared(lease.id,route,context,null);
    }

    /** Поздняя генерация не имеет права отвечать на уже изменённый вопрос, даже если исходящий API успешно завершился. */
    public SpeakingViews.Session completeLive(UUID id,UUID requestId,UUID leaseId,SpeakingViews.AnswerRequest request,SpeakingTutor.Reply reply){
        var session=owned(id,true);
        var lease=generations.findById(leaseId).filter(g->g.studentId.equals(session.studentId)&&g.sessionId.equals(id)&&g.requestId.equals(requestId))
                .orElseThrow(SpeakingSessionService::missing);
        if(!lease.state.equals("PROCESSING")||!lease.expiresAt.isAfter(now())||!lease.requestHash.equals(hash(request)))throw conflict("speaking_generation_expired");
        llm.ensureEnabled();requireVersion(session,lease.sessionVersion);requireActive(session);
        var result=saveAnswer(session,requestId,request,reply,lease.requestHash,lease);lease.close("DONE");return result;
    }
    public void failLive(UUID leaseId){generations.findById(leaseId).filter(g->g.state.equals("PROCESSING")).ifPresent(g->g.close("FAILED"));}

    /** Сервер выбирает текст озвучки из уже проверенной истории, не из произвольного поля браузера. */
    public String speechText(UUID id,UUID turnId){
        var session=owned(id,true);String question;
        if(turnId==null)question=snapshot(session).opening()+"\n\n"+snapshot(session).firstQuestions().get(session.level);
        else question=turns.findById(turnId).filter(t->t.sessionId.equals(id)&&!t.questionText.isBlank())
                .map(t->t.reactionText+"\n\n"+t.questionText).orElseThrow(SpeakingSessionService::missing);
        if(question.isBlank())throw missing();
        var daily=daily(session.studentId);
        if(daily.speechCalls>=32)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"speaking_speech_daily_limit");
        daily.speechCalls++;usage.save(daily);return question;
    }
    private SpeakingDailyUsage daily(Long student){
        var day=LocalDate.now(clock.withZone(ZoneId.of("Asia/Qyzylorda")));
        return usage.findByStudentIdAndDay(student,day).orElseGet(()->{var value=new SpeakingDailyUsage();value.studentId=student;value.day=day;return value;});
    }
    private SpeakingVoiceAttempt voice(SpeakingViews.AnswerRequest request,SpeakingSession session){
        if(request.voiceAttemptId()==null)return null;
        var voice=voiceAttempts.findByIdAndStudentIdAndSessionId(request.voiceAttemptId(),session.studentId,session.id).orElseThrow(SpeakingSessionService::missing);
        voiceStore.requireReady(voice);if(voice.sessionVersion!=session.revision)throw conflict("speaking_version_conflict");return voice;
    }
    private String hash(SpeakingViews.AnswerRequest request){
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(
                (request.expectedVersion()+"\n"+request.voiceAttemptId()+"\n"+request.text().strip()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    record Prepared(UUID leaseId,kz.damulab.ai.AiRuntimeSelection route,LiveSpeakingTutor.Context context,SpeakingViews.Session replay){}

    /** Завершает беседу раньше; уже закрытая сессия возвращается без повторных изменений. */
    public SpeakingViews.Session finish(UUID id, long expectedVersion) {
        var session=owned(id, true);
        if (session.status.equals("ACTIVE")||session.status.equals("PAUSED_SUPPORT")) {
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
            session.assistedNext=true; session.updatedAt=now();
            if(session.helpUses!=null)session.helpUses++;
            sessions.flush();
        }
        return view(session);
    }

    /** Оспоренное замечание немедленно исключается из вычисляемой сводки трудностей. */
    public SpeakingViews.Session dispute(UUID sessionId, UUID observationId, long expectedVersion) {
        var session=owned(sessionId, true);
        var observation=observations.findByIdAndStudentIdAndSessionId(observationId, session.studentId, sessionId)
                .orElseThrow(() -> missing());
        if(observation.evidence.equals("disputed"))return view(session);
        requireVersion(session, expectedVersion);
        observation.evidence="disputed";
        session.updatedAt=now();
        sessions.flush();
        return view(session);
    }

    /** Удаляет свою сессию; БД каскадно удаляет ходы и наблюдения, поэтому статистика не хранит удалённые цитаты. */
    public void delete(UUID id) { sessions.delete(owned(id, true)); sessions.flush(); }

    /** Удаляет всю собственную историю, включая записи за пределами первых 20; дневные квоты сохраняются. */
    public List<UUID> deleteAll(){
        Long student=access.student(true).getId();
        var own=sessions.findByStudentId(student);
        var ids=own.stream().map(s->s.id).toList();
        // FK с cascade удаляют ходы, наблюдения, ASR-черновики и leases в той же транзакции.
        sessions.deleteAll(own);sessions.flush();return ids;
    }

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
        if ((session.status.equals("ACTIVE")||session.status.equals("PAUSED_SUPPORT")) && session.updatedAt.plusMinutes(30).isBefore(now())) {
            session.close("EXPIRED", now()); sessions.flush();
        }
    }
    private SpeakingViews.Session view(SpeakingSession session) {
        var scenario=snapshot(session);
        var savedTurns=turns.findBySessionIdOrderBySequenceNo(session.id);
        var history=savedTurns.stream().map(t ->
                new SpeakingViews.Turn(t.id,t.sequenceNo,t.source,t.learnerText,t.reactionText,t.questionText,t.disposition,t.createdAt,
                        t.requestId,t.providerCode,t.modelName,t.promptVersion)).toList();
        var notes=observations.findBySessionIdOrderByCreatedAt(session.id).stream().map(o ->
                new SpeakingViews.Observation(o.id,o.turnId,o.skillCode,o.original,o.corrected,o.explanation,o.kind,o.evidence,o.source,o.assisted)).toList();
        var focus=feedback.focus(session.studentId,session.scenarioCode,session.id,now());
        SpeakingViews.Summary summary=null;
        if(!session.status.equals("ACTIVE")&&!session.status.equals("PAUSED_SUPPORT")){
            var accepted=savedTurns.stream().filter(t->t.disposition.equals("NORMAL")).toList();
            var recorded=accepted.stream().filter(t->t.source.equals("voice")||t.source.equals("edited")).toList();
            // Длительность принятой записи включает паузы: это не VAD-оценка чистого времени речи.
            summary=new SpeakingViews.Summary(session.acceptedTurns,session.totalTurns,recorded.size(),
                    recorded.stream().mapToInt(t->t.audioSeconds).sum(),session.helpUses,
                    feedback.sessionDifficulties(session.studentId,session.id,now()),
                    feedback.focus(session.studentId,session.scenarioCode,null,now()),session.scenarioCode);
        }
        return new SpeakingViews.Session(session.id,session.status,session.revision,scenario,session.level,session.language,
                scenario.opening(),scenario.firstQuestions().get(session.level),session.acceptedTurns,session.providerVersion.equals("stub-v1")?"STUB":"LIVE",
                transcriber.available(),llm.speechAvailable(),session.offTopicCount>=2,history,notes,focus,summary);
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
