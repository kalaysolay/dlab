package kz.damulab;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.damulab.content.SubjectRepository;
import kz.damulab.notifications.PushDeliveryLogRepository;
import kz.damulab.notifications.PushNotification;
import kz.damulab.notifications.PushNotificationRepository;
import kz.damulab.notifications.PushNotificationService;
import kz.damulab.notifications.PushNotificationStatus;
import kz.damulab.notifications.PushTargetScreen;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PushNotificationIntegrationTest {

    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Almaty");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private PushNotificationRepository pushNotifications;

    @Autowired
    private PushDeliveryLogRepository deliveryLogs;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Test
    void adminCanCreateScheduledQuizPush() throws Exception {
        mockMvc.perform(post("/api/admin/push-notifications")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Через 15 минут стартует викторина",
                                  "scheduled_at": "%s",
                                  "target_screen": "quiz_create_room",
                                  "target_payload": {}
                                }
                                """.formatted(futureServerTime())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/admin/push-notifications/")))
                .andExpect(jsonPath("$.status").value("scheduled"))
                .andExpect(jsonPath("$.target_screen").value("quiz_create_room"))
                .andExpect(jsonPath("$.target_payload").isEmpty());
    }

    @Test
    void subjectTestRequiresAndStoresSubjectPayload() throws Exception {
        Long subjectId = mathSubjectId();

        mockMvc.perform(post("/api/admin/push-notifications")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Открой тест по математике",
                                  "scheduled_at": "%s",
                                  "target_screen": "subject_test",
                                  "target_payload": {"subject_id": %d}
                                }
                                """.formatted(futureServerTime(), subjectId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.target_screen").value("subject_test"))
                .andExpect(jsonPath("$.target_payload.subject_id").value(subjectId.intValue()));
    }

    @Test
    void invalidPushRequestsAreRejected() throws Exception {
        String tooLong = "x".repeat(121);

        mockMvc.perform(post("/api/admin/push-notifications")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "%s",
                                  "scheduled_at": "%s",
                                  "target_screen": "quiz_create_room",
                                  "target_payload": {}
                                }
                                """.formatted(tooLong, futureServerTime())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/push-notifications")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Past push",
                                  "scheduled_at": "%s",
                                  "target_screen": "quiz_create_room",
                                  "target_payload": {}
                                }
                                """.formatted(pastServerTime())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("push_scheduled_at_past"));

        mockMvc.perform(post("/api/admin/push-notifications")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Subject push",
                                  "scheduled_at": "%s",
                                  "target_screen": "subject_test",
                                  "target_payload": {}
                                }
                                """.formatted(futureServerTime())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("push_subject_required"));
    }

    @Test
    void scheduledPushCanBeUpdatedAndCancelledBeforeSend() throws Exception {
        JsonNode created = createQuizPush("Original push");
        Long id = created.get("id").asLong();

        mockMvc.perform(patch("/api/admin/push-notifications/{id}", id)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Updated push",
                                  "scheduled_at": "%s",
                                  "target_screen": "quiz_create_room",
                                  "target_payload": {}
                                }
                                """.formatted(futureServerTime())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Updated push"));

        mockMvc.perform(post("/api/admin/push-notifications/{id}/cancel", id)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));
    }

    @Test
    void dueScheduledPushIsSentByStubProviderAndThenReadOnly() throws Exception {
        PushNotification due = pushNotifications.save(new PushNotification(
                "Due push",
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                PushTargetScreen.QUIZ_CREATE_ROOM,
                objectMapper.writeValueAsString(Map.of()),
                null
        ));

        int processed = pushNotificationService.processDueNotifications();

        org.assertj.core.api.Assertions.assertThat(processed).isGreaterThanOrEqualTo(1);
        PushNotification sent = pushNotifications.findById(due.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(sent.getStatus()).isEqualTo(PushNotificationStatus.SENT);
        org.assertj.core.api.Assertions.assertThat(deliveryLogs.findByPushNotificationIdOrderByAttemptedAtDesc(due.getId()))
                .hasSize(1);

        mockMvc.perform(patch("/api/admin/push-notifications/{id}", due.getId())
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Illegal update",
                                  "scheduled_at": "%s",
                                  "target_screen": "quiz_create_room",
                                  "target_payload": {}
                                }
                                """.formatted(futureServerTime())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("push_not_editable"));
    }

    @Test
    void adminPushPageIsServerRendered() throws Exception {
        mockMvc.perform(get("/admin/push-notifications")
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/push-notifications"))
                .andExpect(content().string(containsString("Push-уведомления")))
                .andExpect(content().string(containsString("data-push-counter")));
    }

    private JsonNode createQuizPush(String text) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/admin/push-notifications")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "%s",
                                  "scheduled_at": "%s",
                                  "target_screen": "quiz_create_room",
                                  "target_payload": {}
                                }
                                """.formatted(text, futureServerTime())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private Long mathSubjectId() {
        return subjects.findAllByOrderByTitleRuAsc().stream()
                .filter(subject -> "math".equals(subject.getCode()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private String futureServerTime() {
        return INPUT_FORMAT.format(LocalDateTime.now(SERVER_ZONE).plusHours(2));
    }

    private String pastServerTime() {
        return INPUT_FORMAT.format(LocalDateTime.now(SERVER_ZONE).minusHours(2));
    }
}
