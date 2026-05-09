package kz.damulab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import kz.damulab.auth.RegisterForm;
import kz.damulab.auth.RegistrationService;
import kz.damulab.content.GradeRepository;
import kz.damulab.content.SubjectRepository;
import kz.damulab.gamification.StudentAchievementRepository;
import kz.damulab.gamification.StudentEngagementService;
import kz.damulab.gamification.StreakRepository;
import kz.damulab.users.RoleCode;
import kz.damulab.users.StudentProfileRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentEngagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private StudentProfileRepository students;

    @Autowired
    private StreakRepository streaks;

    @Autowired
    private StudentAchievementRepository studentAchievements;

    @Autowired
    private StudentEngagementService engagementService;

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private GradeRepository grades;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void finishedTestsUpdateStreakOncePerDayAndAwardAchievementOnce() throws Exception {
        String email = registerStudent("engagement-one");

        finishPerfectSession(email);
        finishPerfectSession(email);

        Long studentId = students.findByUserEmailIgnoreCase(email).orElseThrow().getId();
        assertThat(streaks.findByStudentProfileId(studentId).orElseThrow().getCurrentCount()).isEqualTo(1);
        assertThat(studentAchievements.findByStudentProfileIdOrderByEarnedAtDesc(studentId).stream()
                .filter(item -> "first-test-finished".equals(item.getAchievement().getCode()))
                .count()).isEqualTo(1);

        var dashboard = engagementService.dashboard(email);
        assertThat(dashboard.progress().completedTests()).isEqualTo(2);
        assertThat(dashboard.progress().earnedAchievements()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void finishSessionJsonIncludesFreshAchievementUnlockPayload() throws Exception {
        String email = registerStudent("achievement-unlock-json");
        JsonNode session = startSession(email);
        Long sessionId = session.path("id").asLong();
        for (JsonNode question : session.path("questions")) {
            mockMvc.perform(patch("/api/test-sessions/{sessionId}/answers", sessionId)
                            .with(user(email).roles("STUDENT"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(answerBody(question)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/test-sessions/{sessionId}/finish", sessionId)
                        .with(user(email).roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newlyUnlockedAchievements.length()").value(1))
                .andExpect(jsonPath("$.newlyUnlockedAchievements[0].code").value("first-test-finished"))
                .andExpect(jsonPath("$.newlyUnlockedAchievements[0].title").exists());

        mockMvc.perform(post("/api/test-sessions/{sessionId}/finish", sessionId)
                        .with(user(email).roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newlyUnlockedAchievements.length()").value(0));
    }

    @Test
    void studentDashboardPageShowsMissionStreakAndAchievements() throws Exception {
        String email = registerStudent("engagement-page");

        mockMvc.perform(get("/student")
                        .with(user(email).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("student/dashboard"))
                .andExpect(content().string(containsString("Миссия дня")))
                .andExpect(content().string(containsString("Серия активности")))
                .andExpect(content().string(containsString("Достижения")));
    }

    @Test
    void studentDashboardApiReturnsCurrentEngagementState() throws Exception {
        String email = registerStudent("engagement-api");
        finishPerfectSession(email);

        mockMvc.perform(get("/api/student/dashboard")
                        .with(user(email).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Engagement Student"))
                .andExpect(jsonPath("$.streak.currentCount").value(1))
                .andExpect(jsonPath("$.progress.completedTests").value(1))
                .andExpect(jsonPath("$.achievements[0].code").value("first-test-finished"));
    }

    @Test
    void studentCanUpdateNotificationSettingsInProfile() throws Exception {
        String email = registerStudent("engagement-profile");

        mockMvc.perform(patch("/api/student/profile")
                        .with(user(email).roles("STUDENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Notification Student",
                                  "phone": "+77000000111",
                                  "gradeNo": 4,
                                  "preferredLanguage": "kk",
                                  "lessonRemindersEnabled": false,
                                  "weeklyParentReportEnabled": true,
                                  "sessionResultPushEnabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("kk"))
                .andExpect(jsonPath("$.lessonRemindersEnabled").value(false))
                .andExpect(jsonPath("$.weeklyParentReportEnabled").value(true))
                .andExpect(jsonPath("$.sessionResultPushEnabled").value(true));
    }

    private String registerStudent(String prefix) {
        String email = prefix + "-" + System.nanoTime() + "@example.kz";
        RegisterForm form = new RegisterForm();
        form.setEmail(email);
        form.setPassword("password1");
        form.setFullName("Engagement Student");
        form.setRole(RoleCode.STUDENT);
        form.setGradeNo(4);
        form.setPreferredLanguage("ru");
        registrationService.register(form);
        return email;
    }

    private void finishPerfectSession(String email) throws Exception {
        JsonNode session = startSession(email);
        Long sessionId = session.path("id").asLong();
        for (JsonNode question : session.path("questions")) {
            mockMvc.perform(patch("/api/test-sessions/{sessionId}/answers", sessionId)
                            .with(user(email).roles("STUDENT"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(answerBody(question)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/test-sessions/{sessionId}/finish", sessionId)
                        .with(user(email).roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percent").value(100));
    }

    private JsonNode startSession(String email) throws Exception {
        Long subjectId = subjects.findAllByOrderByTitleRuAsc().stream()
                .filter(subject -> "math".equals(subject.getCode()))
                .findFirst()
                .orElseThrow()
                .getId();
        Long gradeId = grades.findAllByOrderByGradeNoAsc().stream()
                .filter(grade -> Integer.valueOf(4).equals(grade.getGradeNo()))
                .findFirst()
                .orElseThrow()
                .getId();

        String response = mockMvc.perform(post("/api/test-sessions")
                        .with(user(email).roles("STUDENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "testType": "SUBJECT",
                                  "subjectId": %d,
                                  "gradeId": %d,
                                  "language": "ru",
                                  "questionCount": 10
                                }
                                """.formatted(subjectId, gradeId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private String answerBody(JsonNode question) {
        long sessionQuestionId = question.path("id").asLong();
        String type = question.path("type").asText();
        String answer = switch (type) {
            case "SCQ" -> """
                    {"selected":["B"]}
                    """;
            case "MCQ" -> """
                    {"selected":["A","C"]}
                    """;
            case "MATCHING" -> """
                    {"pairs":{"50%":"0.5","25%":"0.25"}}
                    """;
            case "FILL_IN" -> """
                    {"answers":{"[[1]]":"30"}}
                    """;
            default -> "{}";
        };
        return """
                {
                  "sessionQuestionId": %d,
                  "answer": %s
                }
                """.formatted(sessionQuestionId, answer);
    }
}
