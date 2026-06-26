package kz.damulab.notifications;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Запись об одном запуске push-кампании — основа статистики в админке.
 *
 * Создаётся PushCampaignService.execute() при каждом срабатывании расписания.
 * После завершения рассылки заполняются finished_at и счётчики устройств.
 *
 * Таблица: push_campaign_runs.
 */
@Entity
@Table(name = "push_campaign_runs")
public class PushCampaignRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private PushCampaign campaign;

    @Column(name = "triggered_at", nullable = false)
    private OffsetDateTime triggeredAt = OffsetDateTime.now();

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "devices_targeted", nullable = false)
    private int devicesTargeted;

    @Column(name = "devices_sent", nullable = false)
    private int devicesSent;

    @Column(name = "devices_failed", nullable = false)
    private int devicesFailed;

    protected PushCampaignRun() {
    }

    public PushCampaignRun(PushCampaign campaign) {
        this.campaign = campaign;
    }

    /** Завершает запуск: сохраняет итоговые счётчики и метку времени окончания. */
    public void finish(OffsetDateTime finishedAt, int targeted, int sent, int failed) {
        this.finishedAt = finishedAt;
        this.devicesTargeted = targeted;
        this.devicesSent = sent;
        this.devicesFailed = failed;
    }

    public Long getId() { return id; }
    public PushCampaign getCampaign() { return campaign; }
    public OffsetDateTime getTriggeredAt() { return triggeredAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public int getDevicesTargeted() { return devicesTargeted; }
    public int getDevicesSent() { return devicesSent; }
    public int getDevicesFailed() { return devicesFailed; }
}
