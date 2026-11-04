package kz.damulab.speaking;

import java.math.*;
import java.time.*;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Независимый денежный резерв до каждого облачного запроса; ошибки/неизвестный исход не возвращают резерв. */
@Service
class SpeakingBudgetService {
    private final SpeakingBudgetRepository accounts;
    private final SpeakingBudgetProperties prices;
    private final SpeakingAccess access;
    private final Clock clock;
    SpeakingBudgetService(SpeakingBudgetRepository accounts,SpeakingBudgetProperties prices,SpeakingAccess access,Clock clock){
        this.accounts=accounts;this.prices=prices;this.access=access;this.clock=clock;
    }
    boolean priced(SpeakingMetrics.Stage stage,String model){
        return switch(stage){case STT->model.equals("DEEPGRAM:nova-3");case TTS->model.equals("OPENAI:gpt-4o-mini-tts");
            default->prices.getApprovedLlmModels().contains(model);};
    }
    /** REQUIRES_NEW сохраняет расход даже если позднее откатится публикация хода. Никаких текстов здесь нет. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void reserve(SpeakingMetrics.Stage stage,String model){
        if(!priced(stage,model))throw unavailable();
        long amount=micros(switch(stage){case STT->prices.getSttReserveUsd();case TTS->prices.getSpeechReserveUsd();default->prices.getLlmReserveUsd();});
        if(amount==0)throw unavailable();
        long studentLimit=micros(prices.getStudentDailyUsd()),moduleLimit=micros(prices.getModuleDailyUsd());
        Long student=access.student(false).getId();var today=today();
        var module=accounts.lockModule();module.reset(today);
        String key="student/"+student;
        // Создание ученического счётчика тоже под module-lock: первый запрос двух вкладок не обходит unique key.
        var own=accounts.findById(key).orElseGet(()->{var a=new SpeakingBudgetAccount();a.key=key;a.studentId=student;return a;});
        own.reset(today);
        if(amount>studentLimit-own.reserved)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"speaking_budget_student_limit");
        if(amount>moduleLimit-module.reserved)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"speaking_budget_module_limit");
        own.add(amount,stage);module.add(amount,stage);accounts.save(own);accounts.flush();
    }
    @Transactional(readOnly=true)
    public Map<String,Object> report(){
        var row=accounts.findById("module").orElseThrow();boolean current=today().equals(row.day);
        return Map.of("day",today(),"currency","USD","reservedUsd",BigDecimal.valueOf(current?row.reserved:0,6),
                "studentDailyUsd",prices.getStudentDailyUsd(),"moduleDailyUsd",prices.getModuleDailyUsd(),
                "llmCalls",current?row.llm:0,"sttCalls",current?row.stt:0,"speechCalls",current?row.speech:0,
                "accounting","conservative_reservations_not_invoice");
    }
    @org.springframework.scheduling.annotation.Scheduled(initialDelayString="3600000",fixedDelayString="3600000")
    @Transactional public void purge(){accounts.purge(today().minusDays(30));}
    private LocalDate today(){return LocalDate.now(clock.withZone(ZoneId.of("Asia/Qyzylorda")));}
    private long micros(BigDecimal usd){
        try{if(usd==null||usd.signum()<0)throw unavailable();return usd.movePointRight(6).setScale(0,RoundingMode.CEILING).longValueExact();}
        catch(ArithmeticException ex){throw unavailable();}
    }
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"speaking_cost_not_configured");}
}
