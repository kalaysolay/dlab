package kz.damulab;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.UUID;

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
import kz.damulab.parentlink.ParentLinkService;
import kz.damulab.users.RoleCode;
import kz.damulab.users.StudentProfileRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private GradeRepository grades;

    @Autowired
    private StudentProfileRepository students;

    @Autowired
    private ParentLinkService parentLinkService;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void finishedTestUpdatesStudentAnalyticsAndLastErrors() throws Exception {
        JsonNode session = finishSessionWithWrongAnswers();
        Long studentId = students.findByUserEmailIgnoreCase("student@damulab.kz").orElseThrow().getId();

        mockMvc.perform(get("/api/analytics/student/{studentId}/knowledge-map", studentId)
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(0)))
                .andExpect(jsonPath("$[0].topicTitle").isNotEmpty());

        mockMvc.perform(get("/api/analytics/student/{studentId}/last-errors", studentId)
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(0)))
                .andExpect(jsonPath("$[0].sessionId").value(session.path("id").asLong()));

        mockMvc.perform(get("/student/analytics")
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("student/analytics"))
                .andExpect(content().string(containsString("Аналитика знаний")))
                .andExpect(content().string(containsString("Облако знаний")));
    }

    @Test
    void parentSeesAnalyticsOnlyForLinkedChild() throws Exception {
        finishSessionWithWrongAnswers();
        Long studentId = students.findByUserEmailIgnoreCase("student@damulab.kz").orElseThrow().getId();
        String otherParent = "analytics-parent-" + UUID.randomUUID() + "@example.test";
        registerParent(otherParent);

        mockMvc.perform(get("/api/analytics/student/{studentId}/knowledge-map", studentId)
                        .with(user(otherParent).roles("PARENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("student_analytics_not_found"));

        String code = parentLinkService.createStudentLinkCode("student@damulab.kz").code();
        parentLinkService.attachChildByCode(otherParent, code);

        mockMvc.perform(get("/api/analytics/student/{studentId}/knowledge-map", studentId)
                        .with(user(otherParent).roles("PARENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(0)));

        mockMvc.perform(get("/parent/children/{studentId}", studentId)
                        .with(user(otherParent).roles("PARENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Слабые темы")))
                .andExpect(content().string(containsString("Последние ошибки")));
    }

    private JsonNode finishSessionWithWrongAnswers() throws Exception {
        JsonNode session = startSession();
        Long sessionId = session.path("id").asLong();
        for (JsonNode question : session.path("questions")) {
            mockMvc.perform(patch("/api/test-sessions/{sessionId}/answers", sessionId)
                            .with(user("student@damulab.kz").roles("STUDENT"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongAnswerBody(question)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/test-sessions/{sessionId}/finish", sessionId)
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isOk());
        return session;
    }

    private JsonNode startSession() throws Exception {
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
                        .with(user("student@damulab.kz").roles("STUDENT"))
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

    private String wrongAnswerBody(JsonNode question) {
        long sessionQuestionId = question.path("id").asLong();
        String type = question.path("type").asText();
        String answer = switch (type) {
            case "SCQ", "MCQ" -> """
                    {"selected":["Z"]}
                    """;
            case "MATCHING" -> """
                    {"pairs":{"50%":"wrong","25%":"wrong"}}
                    """;
            case "FILL_IN" -> """
                    {"answers":{"[[1]]":"0"}}
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

    private void registerParent(String email) {
        RegisterForm form = new RegisterForm();
        form.setEmail(email);
        form.setPassword("password");
        form.setFullName("Analytics Parent");
        form.setRole(RoleCode.PARENT);
        registrationService.register(form);
    }
}
