package kz.damulab.notifications;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.damulab.audit.AdminContentAuditService;
import kz.damulab.content.Subject;
import kz.damulab.content.SubjectRepository;
import kz.damulab.users.AppUser;
import kz.damulab.users.AppUserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushNotificationService {

    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PushNotificationRepository notifications;
    private final PushDeliveryLogRepository deliveryLogs;
    private final SubjectRepository subjects;
    private final AppUserRepository users;
    private final AdminContentAuditService audit;
    private final PushProvider provider;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ZoneId serverZone;

    public PushNotificationService(
            PushNotificationRepository notifications,
            PushDeliveryLogRepository deliveryLogs,
            SubjectRepository subjects,
            AppUserRepository users,
            AdminContentAuditService audit,
            PushProvider provider,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${damulab.ui.server-time-zone:UTC}") String serverTimeZone
    ) {
        this.notifications = notifications;
        this.deliveryLogs = deliveryLogs;
        this.subjects = subjects;
        this.users = users;
        this.audit = audit;
        this.provider = provider;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.serverZone = ZoneId.of(serverTimeZone);
    }

    @Transactional(readOnly = true)
    public List<PushNotificationResponse> list(PushNotificationStatus status) {
        List<PushNotification> items = status == null
                ? notifications.findAllByOrderByCreatedAtDesc()
                : notifications.findByStatusOrderByCreatedAtDesc(status);
        return items.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PushNotificationResponse get(Long id) {
        return toResponse(findNotification(id));
    }

    @Transactional
    public PushNotificationResponse create(PushNotificationForm form) {
        ValidatedPushPayload payload = validate(form, true);
        PushNotification saved = notifications.save(new PushNotification(
                payload.text(),
                payload.scheduledAtUtc(),
                payload.targetScreen(),
                toJson(payload.targetPayload()),
                currentUser()
        ));
        audit.record("push_created", "PushNotification", saved.getId(), saved.getTargetScreen().apiValue());
        return toResponse(saved);
    }

    @Transactional
    public PushNotificationResponse update(Long id, PushNotificationForm form) {
        PushNotification notification = findNotification(id);
        ValidatedPushPayload payload = validate(form, true);
        notification.update(
                payload.text(),
                payload.scheduledAtUtc(),
                payload.targetScreen(),
                toJson(payload.targetPayload())
        );
        audit.record("push_updated", "PushNotification", notification.getId(), notification.getStatus().apiValue());
        return toResponse(notification);
    }

    @Transactional
    public PushNotificationResponse cancel(Long id) {
        PushNotification notification = findNotification(id);
        notification.cancel();
        audit.record("push_cancelled", "PushNotification", notification.getId(), notification.getTargetScreen().apiValue());
        return toResponse(notification);
    }

    @Transactional
    public int processDueNotifications() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<PushNotification> due = notifications
                .findByStatusAndScheduledAtUtcLessThanEqualOrderByScheduledAtUtcAsc(PushNotificationStatus.SCHEDULED, now);
        for (PushNotification notification : due) {
            PushDeliveryResult result = provider.send(notification);
            deliveryLogs.save(new PushDeliveryLog(notification, result, now));
            if (result.success()) {
                notification.markSent(now);
                audit.record("push_sent", "PushNotification", notification.getId(), result.providerName());
            } else {
                notification.markFailed(result.errorCode(), result.errorMessage(), now);
                audit.record("push_failed", "PushNotification", notification.getId(), result.errorCode());
            }
        }
        return due.size();
    }

    public String serverTimeLabel() {
        ZoneOffset offset = serverZone.getRules().getOffset(OffsetDateTime.now(clock).toInstant());
        String offsetLabel = "Z".equals(offset.getId()) ? "UTC" : "UTC" + offset.getId();
        return "Серверное время (" + offsetLabel + ")";
    }

    public String defaultScheduledAt() {
        return INPUT_FORMAT.format(LocalDateTime.now(clock).atZone(ZoneOffset.UTC)
                .withZoneSameInstant(serverZone)
                .toLocalDateTime()
                .plusHours(1));
    }

    private ValidatedPushPayload validate(PushNotificationForm form, boolean requireFuture) {
        String text = trimToNull(form.getText());
        if (text == null) {
            throw new PushNotificationException("push_text_required");
        }
        if (text.length() > 120) {
            throw new PushNotificationException("push_text_too_long");
        }
        PushTargetScreen targetScreen = form.getTargetScreen();
        if (targetScreen == null) {
            throw new PushNotificationException("push_target_screen_required");
        }
        OffsetDateTime scheduledAtUtc = parseScheduledAt(form.getScheduledAt());
        if (requireFuture && !scheduledAtUtc.isAfter(OffsetDateTime.now(clock))) {
            throw new PushNotificationException("push_scheduled_at_past");
        }
        Map<String, Object> payload = targetPayload(form, targetScreen);
        return new ValidatedPushPayload(text, scheduledAtUtc, targetScreen, payload);
    }

    private Map<String, Object> targetPayload(PushNotificationForm form, PushTargetScreen targetScreen) {
        if (targetScreen == PushTargetScreen.QUIZ_CREATE_ROOM) {
            return Map.of();
        }
        Long subjectId = resolveSubjectId(form);
        if (subjectId == null) {
            throw new PushNotificationException("push_subject_required");
        }
        Subject subject = subjects.findById(subjectId)
                .orElseThrow(() -> new PushNotificationException("subject_not_found"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subject_id", subject.getId());
        payload.put("subject_title_ru", subject.getTitleRu());
        return payload;
    }

    private Long resolveSubjectId(PushNotificationForm form) {
        if (form.getSubjectId() != null) {
            return form.getSubjectId();
        }
        Object raw = form.getTargetPayload().get("subject_id");
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String string && !string.isBlank()) {
            return Long.valueOf(string);
        }
        return null;
    }

    private OffsetDateTime parseScheduledAt(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new PushNotificationException("push_scheduled_at_required");
        }
        try {
            return LocalDateTime.parse(trimmed, INPUT_FORMAT)
                    .atZone(serverZone)
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toOffsetDateTime();
        } catch (DateTimeParseException ex) {
            throw new PushNotificationException("push_scheduled_at_invalid");
        }
    }

    private PushNotification findNotification(Long id) {
        return notifications.findById(id).orElseThrow(() -> new PushNotificationException("push_not_found"));
    }

    private PushNotificationResponse toResponse(PushNotification notification) {
        Map<String, Object> targetPayload = fromJson(notification.getTargetPayloadJson());
        return new PushNotificationResponse(
                notification.getId(),
                notification.getText(),
                INPUT_FORMAT.format(notification.getScheduledAtUtc().atZoneSameInstant(serverZone).toLocalDateTime()),
                notification.getScheduledAtUtc(),
                notification.getTargetScreen().apiValue(),
                notification.getTargetScreen().titleRu(),
                targetPayload,
                notification.getStatus().apiValue(),
                serverZone.getId(),
                serverTimeLabel(),
                notification.getCreatedAt(),
                notification.getUpdatedAt(),
                notification.getSentAt(),
                notification.getCancelledAt(),
                notification.getFailureCode(),
                notification.getFailureMessage()
        );
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new PushNotificationException("push_payload_invalid");
        }
    }

    private Map<String, Object> fromJson(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new PushNotificationException("push_payload_invalid");
        }
    }

    private AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return users.findByEmailIgnoreCase(authentication.getName()).orElse(null);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ValidatedPushPayload(
            String text,
            OffsetDateTime scheduledAtUtc,
            PushTargetScreen targetScreen,
            Map<String, Object> targetPayload
    ) {
    }
}
