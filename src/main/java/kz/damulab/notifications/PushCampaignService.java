package kz.damulab.notifications;

import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.damulab.content.Subject;
import kz.damulab.content.SubjectRepository;
import kz.damulab.gamification.Streak;
import kz.damulab.gamification.StreakRepository;
import kz.damulab.users.StudentProfile;
import kz.damulab.users.StudentProfileRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис управления push-кампаниями (повторяющиеся рассылки).
 *
 * CRUD кампаний — вызывается из AdminPushCampaignApiController и PageController.
 * execute() — вызывается PushCampaignRunner при срабатывании расписания.
 *
 * Переменные в body_template:
 *   {streak} — текущая серия активности пользователя (int),
 *   {name}   — fullName пользователя из app_users.
 *
 * Логика execute():
 *   1. Берёт все активные webpush-подписки (device_tokens).
 *   2. Для каждого токена определяет пользователя → профиль → стрик.
 *   3. Подставляет переменные, строит URL, вызывает PushProvider.sendRaw().
 *   4. Записывает PushCampaignRun со статистикой.
 *   5. Обновляет campaign.last_run_at.
 */
@Service
public class PushCampaignService {

    private static final Logger log = LoggerFactory.getLogger(PushCampaignService.class);

    private final PushCampaignRepository campaigns;
    private final PushCampaignRunRepository campaignRuns;
    private final DeviceTokenRepository deviceTokens;
    private final SubjectRepository subjects;
    private final StudentProfileRepository studentProfiles;
    private final StreakRepository streaks;
    private final PushProvider provider;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ZoneId serverZone;

    public PushCampaignService(
            PushCampaignRepository campaigns,
            PushCampaignRunRepository campaignRuns,
            DeviceTokenRepository deviceTokens,
            SubjectRepository subjects,
            StudentProfileRepository studentProfiles,
            StreakRepository streaks,
            PushProvider provider,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${damulab.ui.server-time-zone:UTC}") String serverTimeZone
    ) {
        this.campaigns = campaigns;
        this.campaignRuns = campaignRuns;
        this.deviceTokens = deviceTokens;
        this.subjects = subjects;
        this.studentProfiles = studentProfiles;
        this.streaks = streaks;
        this.provider = provider;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.serverZone = ZoneId.of(serverTimeZone);
    }

    // ─── CRUD ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PushCampaignResponse> list() {
        return campaigns.findAllByOrderByCreatedAtDesc().stream()
                .map(c -> toResponse(c, campaignRuns.findTop10ByCampaignOrderByTriggeredAtDesc(c)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PushCampaignResponse get(Long id) {
        PushCampaign campaign = findCampaign(id);
        return toResponse(campaign, campaignRuns.findTop10ByCampaignOrderByTriggeredAtDesc(campaign));
    }

    @Transactional
    public PushCampaignResponse create(PushCampaignForm form) {
        ValidatedCampaignPayload payload = validate(form);
        PushCampaign saved = campaigns.save(new PushCampaign(
                payload.name(),
                payload.bodyTemplate(),
                payload.targetScreen(),
                payload.targetPayloadJson(),
                payload.sendTime(),
                payload.daysOfWeek()
        ));
        log.info("push-campaign: created id={} name='{}' send_time={} days={}",
                saved.getId(), saved.getName(), saved.getSendTime(), saved.getDaysOfWeek());
        return toResponse(saved, List.of());
    }

    @Transactional
    public PushCampaignResponse update(Long id, PushCampaignForm form) {
        PushCampaign campaign = findCampaign(id);
        ValidatedCampaignPayload payload = validate(form);
        campaign.update(
                payload.name(),
                payload.bodyTemplate(),
                payload.targetScreen(),
                payload.targetPayloadJson(),
                payload.sendTime(),
                payload.daysOfWeek()
        );
        log.info("push-campaign: updated id={}", id);
        return toResponse(campaign, campaignRuns.findTop10ByCampaignOrderByTriggeredAtDesc(campaign));
    }

    @Transactional
    public PushCampaignResponse toggleEnabled(Long id) {
        PushCampaign campaign = findCampaign(id);
        campaign.setEnabled(!campaign.isEnabled());
        log.info("push-campaign: id={} enabled={}", id, campaign.isEnabled());
        return toResponse(campaign, campaignRuns.findTop10ByCampaignOrderByTriggeredAtDesc(campaign));
    }

    @Transactional
    public void delete(Long id) {
        PushCampaign campaign = findCampaign(id);
        campaigns.delete(campaign);
        log.info("push-campaign: deleted id={}", id);
    }

    // ─── Выполнение кампании ─────────────────────────────────────────────────

    /**
     * Выполняет кампанию: рассылает push всем активным подписчикам с подстановкой переменных.
     * Вызывается PushCampaignRunner при срабатывании расписания.
     *
     * @param campaign кампания для выполнения
     */
    @Transactional
    public void execute(PushCampaign campaign) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        PushCampaignRun run = campaignRuns.save(new PushCampaignRun(campaign));

        List<DeviceToken> tokens = deviceTokens.findByProviderAndEnabledTrue("webpush");
        String targetUrl = buildTargetUrl(campaign);

        int targeted = tokens.size();
        int sent = 0;
        int failed = 0;

        for (DeviceToken token : tokens) {
            String body = resolveBody(campaign.getBodyTemplate(), token);
            PushDeliveryResult result = provider.sendRaw(body, targetUrl, token);
            if (result.success()) {
                sent++;
            } else {
                failed++;
                log.warn("push-campaign: id={} token={} failed: {} {}",
                        campaign.getId(), token.getId(), result.errorCode(), result.errorMessage());
            }
        }

        run.finish(OffsetDateTime.now(clock), targeted, sent, failed);
        campaign.recordRun(now);

        log.info("push-campaign: id={} name='{}' executed: targeted={} sent={} failed={}",
                campaign.getId(), campaign.getName(), targeted, sent, failed);
    }

    // ─── Вспомогательные методы ──────────────────────────────────────────────

    /**
     * Подставляет переменные {streak} и {name} в шаблон тела push.
     * Если профиль или стрик не найден — подставляет нейтральные значения (0 / пустая строка).
     * Public для тестирования.
     */
    public String resolveBody(String bodyTemplate, DeviceToken token) {
        if (!bodyTemplate.contains("{streak}") && !bodyTemplate.contains("{name}")) {
            return bodyTemplate;
        }
        String name = "";
        int streak = 0;
        try {
            Optional<StudentProfile> profileOpt = studentProfiles.findByUserId(
                    // device_tokens.user_id → student_profiles.user_id
                    token.getUser().getId()
            );
            if (profileOpt.isPresent()) {
                StudentProfile profile = profileOpt.get();
                name = profile.getUser().getFullName();
                Optional<Streak> streakOpt = streaks.findByStudentProfileId(profile.getId());
                if (streakOpt.isPresent()) {
                    streak = streakOpt.get().getCurrentCount();
                }
            }
        } catch (Exception ex) {
            // Не блокируем рассылку из-за ошибки подстановки — используем нейтральные значения
            log.warn("push-campaign: failed to resolve variables for token {}: {}", token.getId(), ex.getMessage());
        }
        return bodyTemplate
                .replace("{streak}", String.valueOf(streak))
                .replace("{name}", name);
    }

    /**
     * Строит URL для перехода при клике по push на основе target_screen и payload.
     */
    String buildTargetUrl(PushCampaign campaign) {
        return switch (campaign.getTargetScreen()) {
            case QUIZ_CREATE_ROOM -> "/student/quiz-hub";
            case SUBJECT_TEST -> {
                try {
                    var node = objectMapper.readTree(campaign.getTargetPayloadJson());
                    var subjectId = node.path("subject_id");
                    yield subjectId.isMissingNode()
                            ? "/student/tests"
                            : "/student/tests?subjectId=" + subjectId.asLong();
                } catch (Exception e) {
                    yield "/student/tests";
                }
            }
        };
    }

    private ValidatedCampaignPayload validate(PushCampaignForm form) {
        String name = trimToNull(form.getName());
        if (name == null) throw new PushCampaignException("campaign_name_required");

        String bodyTemplate = trimToNull(form.getBodyTemplate());
        if (bodyTemplate == null) throw new PushCampaignException("campaign_body_required");
        if (bodyTemplate.length() > 500) throw new PushCampaignException("campaign_body_too_long");

        if (form.getTargetScreen() == null) throw new PushCampaignException("campaign_target_screen_required");

        String sendTimeStr = trimToNull(form.getSendTime());
        if (sendTimeStr == null) throw new PushCampaignException("campaign_send_time_required");
        LocalTime sendTime;
        try {
            sendTime = LocalTime.parse(sendTimeStr);
        } catch (DateTimeParseException ex) {
            throw new PushCampaignException("campaign_send_time_invalid");
        }

        String daysOfWeek = trimToNull(form.getDaysOfWeek());
        if (daysOfWeek == null) daysOfWeek = "ALL";

        String targetPayloadJson = buildTargetPayloadJson(form);

        return new ValidatedCampaignPayload(
                name, bodyTemplate, form.getTargetScreen(), targetPayloadJson, sendTime, daysOfWeek
        );
    }

    private String buildTargetPayloadJson(PushCampaignForm form) {
        if (form.getTargetScreen() != PushTargetScreen.SUBJECT_TEST) {
            return "{}";
        }
        Long subjectId = form.getSubjectId();
        if (subjectId == null) throw new PushCampaignException("campaign_subject_required");
        Subject subject = subjects.findById(subjectId)
                .orElseThrow(() -> new PushCampaignException("subject_not_found"));
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "subject_id", subject.getId(),
                    "subject_title_ru", subject.getTitleRu()
            ));
        } catch (JsonProcessingException ex) {
            throw new PushCampaignException("campaign_payload_invalid");
        }
    }

    private PushCampaign findCampaign(Long id) {
        return campaigns.findById(id)
                .orElseThrow(() -> new PushCampaignException("campaign_not_found"));
    }

    private PushCampaignResponse toResponse(PushCampaign c, List<PushCampaignRun> recentRuns) {
        return new PushCampaignResponse(
                c.getId(),
                c.getName(),
                c.getBodyTemplate(),
                c.getTargetScreen().apiValue(),
                c.getTargetScreen().titleRu(),
                c.getSendTime().toString(),
                c.getDaysOfWeek(),
                c.isEnabled(),
                c.getLastRunAt(),
                c.getCreatedAt(),
                recentRuns.stream().map(r -> new PushCampaignResponse.RunSummary(
                        r.getId(),
                        r.getTriggeredAt(),
                        r.getFinishedAt(),
                        r.getDevicesTargeted(),
                        r.getDevicesSent(),
                        r.getDevicesFailed()
                )).toList()
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ValidatedCampaignPayload(
            String name,
            String bodyTemplate,
            PushTargetScreen targetScreen,
            String targetPayloadJson,
            LocalTime sendTime,
            String daysOfWeek
    ) {}
}
