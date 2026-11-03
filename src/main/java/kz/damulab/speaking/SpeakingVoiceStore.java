package kz.damulab.speaking;

import java.time.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Короткие транзакции вокруг ASR. Профиль блокируется только для квоты и публикации результата. */
@Service
@Transactional
class SpeakingVoiceStore {
    private final SpeakingAccess access;
    private final SpeakingSessionRepository sessions;
    private final SpeakingVoiceAttemptRepository attempts;
    private final SpeakingDailyUsageRepository usage;
    private final Clock clock;
    SpeakingVoiceStore(SpeakingAccess access,SpeakingSessionRepository sessions,SpeakingVoiceAttemptRepository attempts,
            SpeakingDailyUsageRepository usage,Clock clock) {
        this.access=access; this.sessions=sessions; this.attempts=attempts; this.usage=usage; this.clock=clock;
    }

    /** Атомарно резервирует минуту дневного бюджета; повтор того же запроса не запускает второй ASR. */
    Reservation begin(UUID sessionId,UUID requestId,long version,String hash) {
        Long student=access.student(true).getId();
        var session=sessions.findByIdAndStudentId(sessionId,student).orElseThrow(()->error(404,"speaking_not_found"));
        var replay=attempts.findBySessionIdAndRequestId(sessionId,requestId);
        if(replay.isPresent()) {
            var old=replay.get();
            if(!old.audioHash.equals(hash) || old.sessionVersion!=version) throw error(409,"speaking_request_reused");
            requireReady(old);
            return new Reservation(old.id,false,view(old));
        }
        requireQuestion(session,version);
        var now=OffsetDateTime.now(clock);
        attempts.findByStudentIdAndActiveSlot(student,1).ifPresent(old->{
            // После аварии JVM lease истекает: забытая обработка не блокирует ученика навсегда.
            if(old.createdAt.plusMinutes(3).isAfter(now)) throw error(409,"speaking_voice_processing");
            old.close("FAILED"); old.transcript=null; attempts.flush();
        });
        var day=now.atZoneSameInstant(ZoneId.of("Asia/Qyzylorda")).toLocalDate();
        var daily=usage.findByStudentIdAndDay(student,day).orElseGet(()->{
            var item=new SpeakingDailyUsage(); item.studentId=student; item.day=day; return item;
        });
        if(daily.audioSeconds+60>900) throw error(429,"speaking_audio_daily_limit");
        if(daily.voiceWindow==null || !daily.voiceWindow.plusMinutes(1).isAfter(now)) {
            daily.voiceWindow=now; daily.voiceRequests=0;
        }
        if(daily.voiceRequests>=6) throw error(429,"speaking_voice_rate_limit");
        // Неудачи тоже расходуют резерв: неверные файлы не позволяют бесплатно перегружать декодер.
        daily.audioSeconds+=60; daily.voiceRequests++; usage.save(daily);
        var item=new SpeakingVoiceAttempt(); item.studentId=student; item.sessionId=sessionId; item.requestId=requestId;
        item.sessionVersion=version; item.audioHash=hash; item.usageDay=day; item.createdAt=now; item.expiresAt=now.plusMinutes(15);
        attempts.saveAndFlush(item);
        return new Reservation(item.id,true,null);
    }

    /** Принимает результат только для того же вопроса; поздняя расшифровка не меняет новую беседу. */
    SpeakingViews.Transcription complete(UUID id,SpeakingTranscriber.Transcript transcript) {
        Long student=access.student(true).getId();
        var item=attempts.findById(id).filter(a->a.studentId.equals(student)).orElseThrow(()->error(404,"speaking_not_found"));
        if(!item.state.equals("PROCESSING") || !item.createdAt.plusMinutes(3).isAfter(OffsetDateTime.now(clock)))
            throw error(409,"speaking_voice_expired");
        if(transcript.seconds()<1 || transcript.seconds()>60 || transcript.text()==null || transcript.text().isBlank()
                || transcript.text().length()>2000) throw error(503,"speaking_voice_failed");
        var session=sessions.findByIdAndStudentId(item.sessionId,student).orElseThrow(()->error(404,"speaking_not_found"));
        requireQuestion(session,item.sessionVersion);
        var daily=usage.findByStudentIdAndDay(student,item.usageDay).orElseThrow();
        daily.audioSeconds-=60-transcript.seconds();
        item.audioSeconds=transcript.seconds(); item.transcript=transcript.text().strip(); item.close("READY");
        return view(item);
    }

    /** Ошибка удаляет черновой текст, но не отменяет защитный бюджет. Содержимое не журналируется. */
    void fail(UUID id) {
        attempts.findById(id).filter(a->a.state.equals("PROCESSING")).ifPresent(a->{a.close("FAILED"); a.transcript=null;});
    }

    /** Ученик может явно отбросить расшифровку; повторное удаление безопасно. */
    void discard(UUID sessionId,UUID id) {
        Long student=access.student(true).getId();
        var item=attempts.findByIdAndStudentIdAndSessionId(id,student,sessionId).orElseThrow(()->error(404,"speaking_not_found"));
        if(!item.state.equals("CONSUMED")) { item.close("CANCELLED"); item.transcript=null; }
    }

    void requireReady(SpeakingVoiceAttempt item) {
        if(!item.expiresAt.isAfter(OffsetDateTime.now(clock))) throw error(409,"speaking_voice_expired");
        if(item.state.equals("PROCESSING")) throw error(409,"speaking_voice_processing");
        if(!item.state.equals("READY") || !item.expiresAt.isAfter(OffsetDateTime.now(clock)))
            throw error(409,"speaking_voice_expired");
    }
    /** TTL работает и при выключенном модуле: непринятые тексты исчезают не позднее следующей минуты. */
    @org.springframework.scheduling.annotation.Scheduled(initialDelayString="60000",fixedDelayString="60000")
    public void purge() { attempts.deleteByExpiresAtLessThanEqual(OffsetDateTime.now(clock)); }
    private void requireQuestion(SpeakingSession session,long version) {
        if(!session.status.equals("ACTIVE") || session.updatedAt.plusMinutes(30).isBefore(OffsetDateTime.now(clock)))
            throw error(409,"speaking_session_closed");
        if(session.revision!=version) throw error(409,"speaking_version_conflict");
    }
    private SpeakingViews.Transcription view(SpeakingVoiceAttempt a) {
        return new SpeakingViews.Transcription(a.id,a.sessionVersion,a.transcript,a.audioSeconds,a.expiresAt);
    }
    private static ResponseStatusException error(int status,String code) { return new ResponseStatusException(HttpStatus.valueOf(status),code); }
    record Reservation(UUID id,boolean fresh,SpeakingViews.Transcription replay) {}
}
