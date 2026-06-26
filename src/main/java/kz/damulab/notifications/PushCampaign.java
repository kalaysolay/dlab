package kz.damulab.notifications;

import java.time.LocalTime;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Правило автоматической повторяющейся push-рассылки (кампания).
 *
 * В отличие от PushNotification (разовый outbox), кампания — это расписание:
 * каждый день PushCampaignRunner проверяет, совпадает ли текущее время (в серверной
 * тайм-зоне) с send_time, и запускает рассылку всем активным подписчикам.
 *
 * body_template поддерживает переменные:
 *   {streak} — текущая серия дней активности пользователя (берётся из streaks),
 *   {name}   — имя пользователя из профиля.
 * Подстановка выполняется per-device в PushCampaignService.execute().
 *
 * Таблица: push_campaigns.
 */
@Entity
@Table(name = "push_campaigns")
public class PushCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Название кампании для отображения в админке. Не попадает в push. */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Текст push-уведомления. Поддерживает переменные {streak} и {name}.
     * Подстановка выполняется перед отправкой каждому устройству.
     */
    @Column(name = "body_template", nullable = false, length = 500)
    private String bodyTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_screen", nullable = false, length = 64)
    private PushTargetScreen targetScreen;

    /** JSON-параметры экрана-назначения (например, subject_id для subject_test). */
    @Column(name = "target_payload_json", nullable = false, columnDefinition = "text")
    private String targetPayloadJson = "{}";

    /**
     * Время отправки в серверной тайм-зоне (Asia/Almaty по умолчанию).
     * Планировщик запускается каждую минуту и сравнивает HH:mm с этим значением.
     */
    @Column(name = "send_time", nullable = false)
    private LocalTime sendTime;

    /**
     * Дни недели для рассылки.
     * "ALL" — каждый день; иначе список через запятую: "MON,TUE,WED,THU,FRI,SAT,SUN".
     */
    @Column(name = "days_of_week", nullable = false, length = 50)
    private String daysOfWeek = "ALL";

    /** Кампания активна. Выключенные кампании планировщик пропускает. */
    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Метка последнего запуска (UTC). Используется для предотвращения
     * повторного срабатывания в течение одного дня (проверяем дату, не время).
     */
    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected PushCampaign() {
    }

    public PushCampaign(String name, String bodyTemplate, PushTargetScreen targetScreen,
                        String targetPayloadJson, LocalTime sendTime, String daysOfWeek) {
        this.name = name;
        this.bodyTemplate = bodyTemplate;
        this.targetScreen = targetScreen;
        this.targetPayloadJson = targetPayloadJson;
        this.sendTime = sendTime;
        this.daysOfWeek = daysOfWeek;
    }

    /** Обновляет поля кампании. Вызывается при редактировании через API. */
    public void update(String name, String bodyTemplate, PushTargetScreen targetScreen,
                       String targetPayloadJson, LocalTime sendTime, String daysOfWeek) {
        this.name = name;
        this.bodyTemplate = bodyTemplate;
        this.targetScreen = targetScreen;
        this.targetPayloadJson = targetPayloadJson;
        this.sendTime = sendTime;
        this.daysOfWeek = daysOfWeek;
        this.updatedAt = OffsetDateTime.now();
    }

    /** Переключает статус включена/выключена. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Фиксирует факт запуска. Вызывается после успешного выполнения кампании,
     * чтобы планировщик не запустил её повторно в тот же день.
     */
    public void recordRun(OffsetDateTime now) {
        this.lastRunAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getBodyTemplate() { return bodyTemplate; }
    public PushTargetScreen getTargetScreen() { return targetScreen; }
    public String getTargetPayloadJson() { return targetPayloadJson; }
    public LocalTime getSendTime() { return sendTime; }
    public String getDaysOfWeek() { return daysOfWeek; }
    public boolean isEnabled() { return enabled; }
    public OffsetDateTime getLastRunAt() { return lastRunAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
