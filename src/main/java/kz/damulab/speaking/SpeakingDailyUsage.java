package kz.damulab.speaking;

import jakarta.persistence.*;
import java.time.LocalDate;

/** Дневная статистика стартов и квоты аудио/облака. Удаление разговора не обнуляет расход. Без содержимого беседы. */
@Entity
@Table(name="speaking_daily_usage")
class SpeakingDailyUsage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(name="student_profile_id", nullable=false) Long studentId;
    @Column(name="usage_day", nullable=false) LocalDate day;
    @Column(name="sessions_started", nullable=false) int sessionsStarted;
    @Column(name="audio_seconds", nullable=false) int audioSeconds;
    @Column(name="llm_calls",nullable=false) int llmCalls;
    @Column(name="speech_calls",nullable=false) int speechCalls;
    @Column(name="voice_window") java.time.OffsetDateTime voiceWindow;
    @Column(name="voice_requests", nullable=false) int voiceRequests;
    protected SpeakingDailyUsage() {}
}
