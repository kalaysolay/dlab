package kz.damulab.notifications;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO для создания и редактирования push-кампании.
 * Используется как в HTML-форме (Thymeleaf ModelAttribute), так и в REST API.
 *
 * send_time передаётся строкой "HH:mm" и парсится в PushCampaignService.
 * days_of_week: "ALL" или перечисление дней через запятую "MON,TUE,WED,THU,FRI,SAT,SUN".
 */
public class PushCampaignForm {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Size(max = 500)
    @JsonAlias("body_template")
    private String bodyTemplate;

    @NotNull
    @JsonAlias("target_screen")
    private PushTargetScreen targetScreen = PushTargetScreen.QUIZ_CREATE_ROOM;

    /** subject_id для экрана subject_test. Опционален, берётся при необходимости из targetPayloadJson. */
    private Long subjectId;

    /**
     * Время отправки в серверной тайм-зоне, формат "HH:mm" (например "11:00").
     */
    @NotBlank
    @JsonAlias("send_time")
    private String sendTime;

    /**
     * Дни недели: "ALL" или список "MON,TUE,WED,THU,FRI,SAT,SUN".
     * По умолчанию — каждый день.
     */
    @JsonAlias("days_of_week")
    private String daysOfWeek = "ALL";

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @JsonProperty("body_template")
    public String getBodyTemplate() { return bodyTemplate; }
    public void setBodyTemplate(String bodyTemplate) { this.bodyTemplate = bodyTemplate; }

    @JsonProperty("target_screen")
    public PushTargetScreen getTargetScreen() { return targetScreen; }
    public void setTargetScreen(PushTargetScreen targetScreen) { this.targetScreen = targetScreen; }

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

    @JsonProperty("send_time")
    public String getSendTime() { return sendTime; }
    public void setSendTime(String sendTime) { this.sendTime = sendTime; }

    @JsonProperty("days_of_week")
    public String getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(String daysOfWeek) { this.daysOfWeek = daysOfWeek; }
}
