package kz.damulab.speaking;

import jakarta.persistence.*;
import java.time.LocalDate;

/** Дневной счётчик запуска: удаление разговора не должно обходить квоту. Не хранит содержимое беседы. */
@Entity
@Table(name="speaking_daily_usage")
class SpeakingDailyUsage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(name="student_profile_id", nullable=false) Long studentId;
    @Column(name="usage_day", nullable=false) LocalDate day;
    @Column(name="sessions_started", nullable=false) int sessionsStarted;
    protected SpeakingDailyUsage() {}
}
