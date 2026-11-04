package kz.damulab.speaking;

import jakarta.persistence.*;
import java.time.LocalDate;

/** Только резерв денег и число внешних запросов. Удаление истории не обнуляет дневной лимит. */
@Entity @Table(name="speaking_budget_accounts")
class SpeakingBudgetAccount {
    @Id @Column(name="scope_key",length=64) String key;
    @Column(name="student_profile_id") Long studentId;
    @Column(name="usage_day",nullable=false) LocalDate day;
    @Column(name="reserved_micros",nullable=false) long reserved;
    @Column(name="llm_calls",nullable=false) long llm;
    @Column(name="stt_calls",nullable=false) long stt;
    @Column(name="speech_calls",nullable=false) long speech;
    void reset(LocalDate today){if(!today.equals(day)){day=today;reserved=0;llm=0;stt=0;speech=0;}}
    void add(long micros,SpeakingMetrics.Stage stage){reserved=Math.addExact(reserved,micros);switch(stage){case STT->stt++;case TTS->speech++;default->llm++;}}
}
