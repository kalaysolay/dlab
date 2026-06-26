package kz.damulab.notifications;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Планировщик автоматических push-кампаний.
 *
 * Каждую минуту проверяет все включённые кампании:
 *   1. Переводит текущее UTC-время в серверную тайм-зону (Asia/Almaty).
 *   2. Сравнивает HH:mm с campaign.send_time (с точностью до минуты).
 *   3. Проверяет совпадение дня недели с campaign.days_of_week.
 *   4. Проверяет, что кампания не запускалась сегодня (last_run_at).
 *   5. При совпадении всех условий вызывает PushCampaignService.execute().
 *
 * days_of_week формат: "ALL" — каждый день, иначе список сокращений через запятую
 * в формате английских трёхбуквенных названий дней (MON,TUE,WED,THU,FRI,SAT,SUN).
 *
 * Намеренное ограничение: fixedDelay не используется — cron «0 * * * * *» гарантирует
 * точное срабатывание в начале каждой минуты, что необходимо для корректного сравнения времени.
 */
@Component
public class PushCampaignRunner {

    private static final Logger log = LoggerFactory.getLogger(PushCampaignRunner.class);

    private final PushCampaignRepository campaigns;
    private final PushCampaignService campaignService;
    private final Clock clock;
    private final ZoneId serverZone;

    public PushCampaignRunner(
            PushCampaignRepository campaigns,
            PushCampaignService campaignService,
            Clock clock,
            @Value("${damulab.ui.server-time-zone:UTC}") String serverTimeZone
    ) {
        this.campaigns = campaigns;
        this.campaignService = campaignService;
        this.clock = clock;
        this.serverZone = ZoneId.of(serverTimeZone);
    }

    @Scheduled(cron = "0 * * * * *")
    public void checkAndFire() {
        ZonedDateTime nowLocal = ZonedDateTime.now(clock).withZoneSameInstant(serverZone);
        LocalTime nowTime = nowLocal.toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        LocalDate nowDate = nowLocal.toLocalDate();
        DayOfWeek nowDow = nowLocal.getDayOfWeek();

        List<PushCampaign> enabled = campaigns.findByEnabledTrueOrderByCreatedAtDesc();
        for (PushCampaign campaign : enabled) {
            if (!timeMaches(campaign, nowTime)) continue;
            if (!dayMatches(campaign, nowDow)) continue;
            if (alreadyRanToday(campaign, nowDate)) continue;

            log.debug("push-campaign: firing id={} '{}' at {}", campaign.getId(), campaign.getName(), nowTime);
            try {
                campaignService.execute(campaign);
            } catch (Exception ex) {
                log.error("push-campaign: id={} execution failed: {}", campaign.getId(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * Сравнивает время кампании с текущим временем с точностью до минуты.
     * campaign.send_time уже хранится как LocalTime, усечём до минут для корректного сравнения.
     */
    private boolean timeMaches(PushCampaign campaign, LocalTime nowMinute) {
        LocalTime campaignTime = campaign.getSendTime().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        return campaignTime.equals(nowMinute);
    }

    /**
     * Проверяет, входит ли текущий день недели в расписание кампании.
     * "ALL" — все дни; иначе проверяем наличие трёхбуквенного кода дня в списке.
     */
    private boolean dayMatches(PushCampaign campaign, DayOfWeek nowDow) {
        String daysOfWeek = campaign.getDaysOfWeek();
        if ("ALL".equalsIgnoreCase(daysOfWeek)) return true;

        // Текущий день недели в коротком формате (MON, TUE, ...)
        String todayCode = nowDow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase();
        Set<String> configured = Stream.of(daysOfWeek.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        return configured.contains(todayCode);
    }

    /**
     * Проверяет, запускалась ли кампания сегодня (по серверной тайм-зоне).
     * Предотвращает повторное срабатывание при рестарте сервиса в ту же минуту.
     */
    private boolean alreadyRanToday(PushCampaign campaign, LocalDate today) {
        if (campaign.getLastRunAt() == null) return false;
        LocalDate lastRunDate = campaign.getLastRunAt()
                .atZoneSameInstant(serverZone)
                .toLocalDate();
        return lastRunDate.equals(today);
    }
}
