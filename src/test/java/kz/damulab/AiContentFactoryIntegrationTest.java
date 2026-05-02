package kz.damulab;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.damulab.ai.AiGenerationJobRepository;
import kz.damulab.content.GradeRepository;
import kz.damulab.content.SubjectRepository;

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
class AiContentFactoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private GradeRepository grades;

    @Autowired
    private AiGenerationJobRepository jobs;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanCreateAiQuestionGenerationJob() throws Exception {
        Long topicId = createTopic("ai-job-topic-");

        mockMvc.perform(post("/api/admin/ai/questions/generate")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generationBody(topicId, "Make a life case")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/admin/ai/jobs/")))
                .andExpect(jsonPath("$.status").value("succeeded"))
                .andExpect(jsonPath("$.providerName").value("stub"))
                .andExpect(jsonPath("$.items[0].reviewStatus").value("pending"));
    }

    @Test
    void providerFailureCanBeRetriedWithoutNetworkCalls() throws Exception {
        Long topicId = createTopic("ai-failure-topic-");
        JsonNode failed = performGeneration(topicId, "__FAIL_PROVIDER__");
        Long jobId = failed.get("id").asLong();

        org.assertj.core.api.Assertions.assertThat(failed.get("status").asText()).isEqualTo("failed");
        org.assertj.core.api.Assertions.assertThat(failed.get("errorCode").asText()).isEqualTo("stub_provider_failure");

        mockMvc.perform(post("/api/admin/ai/jobs/{jobId}/retry", jobId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.retryCount").value(1));
    }

    @Test
    void approvingAiItemCreatesNeedsReviewQuestionButDoesNotPublish() throws Exception {
        Long topicId = createTopic("ai-no-autopublish-topic-");
        JsonNode job = performGeneration(topicId, "Regular draft");
        Long batchId = job.get("batchId").asLong();
        Long itemId = job.get("items").get(0).get("id").asLong();

        JsonNode approved = objectMapper.readTree(mockMvc.perform(post(
                                "/api/admin/ai/batches/{batchId}/items/{itemId}/approve",
                                batchId,
                                itemId
                        )
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("approved"))
                .andExpect(jsonPath("$.createdQuestionId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString());

        Long questionId = approved.get("createdQuestionId").asLong();
        mockMvc.perform(get("/api/admin/questions/{id}", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("needs_review"))
                .andExpect(jsonPath("$.status").value(not("published")));
    }

    @Test
    void stubCanGenerateAndApproveMatchingAndFillInDrafts() throws Exception {
        Long topicId = createTopic("ai-multi-type-topic-");

        JsonNode matchingJob = performGeneration(topicId, "MATCHING", "matching draft");
        approveFirstItem(matchingJob)
                .andExpect(jsonPath("$.reviewStatus").value("approved"))
                .andExpect(jsonPath("$.questionType").value("MATCHING"));

        JsonNode fillJob = performGeneration(topicId, "FILL_IN", "fill draft");
        approveFirstItem(fillJob)
                .andExpect(jsonPath("$.reviewStatus").value("approved"))
                .andExpect(jsonPath("$.questionType").value("FILL_IN"));
    }

    @Test
    void outboundProviderDtoDoesNotContainPiiOrDirectIdentifiers() throws Exception {
        Long topicId = createTopic("ai-pii-topic-");
        JsonNode job = performGeneration(
                topicId,
                "student_id=42 parent_id=81 user_id=99 link code=ABC123 email test@example.com phone +77011234567"
        );
        String payload = jobs.findById(job.get("id").asLong()).orElseThrow().getRequestPayloadJson();

        org.assertj.core.api.Assertions.assertThat(payload)
                .doesNotContain("topicId")
                .doesNotContain("student_id=42")
                .doesNotContain("parent_id=81")
                .doesNotContain("user_id=99")
                .doesNotContain("ABC123")
                .doesNotContain("test@example.com")
                .doesNotContain("+77011234567");
        org.assertj.core.api.Assertions.assertThat(payload)
                .contains("[redacted-id]")
                .contains("[redacted-email]")
                .contains("[redacted-phone]");
    }

    @Test
    void adminAiGenerationPageIsServerRendered() throws Exception {
        mockMvc.perform(get("/admin/questions/ai-generate")
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/question-ai-generate"))
                .andExpect(content().string(containsString("AI генерация вопросов")));
    }

    private JsonNode performGeneration(Long topicId, String instruction) throws Exception {
        return performGeneration(topicId, "SCQ", instruction);
    }

    private JsonNode performGeneration(Long topicId, String questionType, String instruction) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/admin/ai/questions/generate")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generationBody(topicId, questionType, instruction)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private String generationBody(Long topicId, String instruction) {
        return generationBody(topicId, "SCQ", instruction);
    }

    private String generationBody(Long topicId, String questionType, String instruction) {
        return """
                {
                  "topicId": %d,
                  "questionType": "%s",
                  "difficulty": 2,
                  "count": 3,
                  "languageMode": "RU_KK",
                  "instruction": "%s"
                }
                """.formatted(topicId, questionType, instruction);
    }

    private org.springframework.test.web.servlet.ResultActions approveFirstItem(JsonNode job) throws Exception {
        Long batchId = job.get("batchId").asLong();
        Long itemId = job.get("items").get(0).get("id").asLong();
        return mockMvc.perform(post(
                        "/api/admin/ai/batches/{batchId}/items/{itemId}/approve",
                        batchId,
                        itemId
                )
                .with(user("admin@damulab.kz").roles("ADMIN"))
                .with(csrf()));
    }

    private Long createTopic(String prefix) throws Exception {
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
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String response = mockMvc.perform(post("/api/admin/topics")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": %d,
                                  "gradeId": %d,
                                  "code": "%s%s",
                                  "titleRu": "AI topic %s",
                                  "titleKk": "AI topic %s"
                                }
                                """.formatted(subjectId, gradeId, prefix, suffix, suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return idFrom(response);
    }

    private Long idFrom(String response) {
        int start = response.indexOf("\"id\":") + 5;
        int end = response.indexOf(',', start);
        return Long.valueOf(response.substring(start, end));
    }
}
