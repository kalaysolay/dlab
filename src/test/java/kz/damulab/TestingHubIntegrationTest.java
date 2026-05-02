package kz.damulab;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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

import kz.damulab.content.GradeRepository;
import kz.damulab.content.SubjectRepository;
import kz.damulab.testing.TestResultRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TestingHubIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private GradeRepository grades;

    @Autowired
    private TestResultRepository results;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void studentCanStartAnswerAndFinishSessionWithoutReceivingAnswerKey() throws Exception {
        JsonNode session = startSession();

        org.assertj.core.api.Assertions.assertThat(session.path("questions").size()).isGreaterThanOrEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(session.toString()).doesNotContain("answerKey").doesNotContain("correctAnswer");

        Long sessionId = session.path("id").asLong();
        for (JsonNode question : session.path("questions")) {
            mockMvc.perform(patch("/api/test-sessions/{sessionId}/answers", sessionId)
                            .with(user("student@damulab.kz").roles("STUDENT"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(answerBody(question)))
                    .andExpect(status().isOk());
        }

        long beforeFinish = results.count();
        mockMvc.perform(post("/api/test-sessions/{sessionId}/finish", sessionId)
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuestions").value(session.path("questions").size()))
                .andExpect(jsonPath("$.percent").value(100))
                .andExpect(content().string(containsString("correctAnswer")));

        mockMvc.perform(post("/api/test-sessions/{sessionId}/finish", sessionId)
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percent").value(100));

        org.assertj.core.api.Assertions.assertThat(results.count()).isEqualTo(beforeFinish + 1);
    }

    @Test
    void studentTestingPagesAreServerRendered() throws Exception {
        mockMvc.perform(get("/student/tests")
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("student/tests"))
                .andExpect(content().string(containsString("Testing Hub")));

        JsonNode session = startSession();

        mockMvc.perform(get("/student/test-sessions/{sessionId}", session.path("id").asLong())
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("student/test-session"))
                .andExpect(content().string(containsString("Тестовая сессия")))
                .andExpect(content().string(not(containsString("answer_key_json"))));
    }

    @Test
    void parentCannotOpenTestingHubApi() throws Exception {
        mockMvc.perform(get("/api/test-sessions/1")
                        .with(user("parent@damulab.kz").roles("PARENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void fillInResultPageRendersPlaceholdersWithoutObjectObject() throws Exception {
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
        String marker = "fill-" + System.nanoTime();

        String topicPayload = mockMvc.perform(post("/api/admin/topics")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": %d,
                                  "gradeId": %d,
                                  "code": "%s",
                                  "titleRu": "Fill topic %s",
                                  "titleKk": "Fill topic %s"
                                }
                                """.formatted(subjectId, gradeId, marker, marker, marker)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long topicId = objectMapper.readTree(topicPayload).path("id").asLong();

        String questionPayload = mockMvc.perform(post("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "type": "FILL_IN",
                                  "difficulty": 5,
                                  "bodyRu": "Завершите: [[1]] + [[2]] = 10",
                                  "bodyKk": "Толықтырыңыз: [[1]] + [[2]] = 10",
                                  "source": "fill-result-%s",
                                  "fillAnswers": [
                                    {"placeholder":"[[1]]","answer":"4","mode":"EXACT"},
                                    {"placeholder":"[[2]]","answer":"6","mode":"EXACT"}
                                  ]
                                }
                                """.formatted(topicId, marker)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long questionId = objectMapper.readTree(questionPayload).path("id").asLong();

        mockMvc.perform(post("/api/admin/questions/{id}/approve", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/questions/{id}/publish", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());

        String sessionResponse = mockMvc.perform(post("/api/test-sessions")
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "testType": "SUBJECT",
                                  "subjectId": %d,
                                  "gradeId": %d,
                                  "language": "ru",
                                  "difficulty": 5,
                                  "questionCount": 1
                                }
                                """.formatted(subjectId, gradeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.questions[0].type").value("FILL_IN"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode session = objectMapper.readTree(sessionResponse);
        Long sessionId = session.path("id").asLong();
        Long sessionQuestionId = session.path("questions").get(0).path("id").asLong();

        mockMvc.perform(patch("/api/test-sessions/{sessionId}/answers", sessionId)
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionQuestionId": %d,
                                  "answer": {"answers":{"[[1]]":"4","[[2]]":"6"}}
                                }
                                """.formatted(sessionQuestionId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/test-sessions/{sessionId}/finish", sessionId)
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].type").value("FILL_IN"));

        mockMvc.perform(get("/student/test-sessions/{sessionId}", sessionId)
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(
                        "/student/test-sessions/" + sessionId + "/result"));

        mockMvc.perform(get("/student/test-sessions/{sessionId}/result", sessionId)
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("student/test-result"))
                .andExpect(content().string(containsString("result-fill-student")))
                .andExpect(content().string(containsString("[[1]]")))
                .andExpect(content().string(not(containsString("[object Object]"))));
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
                .andExpect(jsonPath("$.status").value("in_progress"))
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
