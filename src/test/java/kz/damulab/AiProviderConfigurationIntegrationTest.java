package kz.damulab;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.damulab.content.GradeRepository;
import kz.damulab.content.SubjectRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "damulab.ai.provider=openai",
        "damulab.ai.fallback-provider=deepseek",
        "damulab.ai.real-providers-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiProviderConfigurationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private GradeRepository grades;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void realProviderCallsAreBlockedWhenFeatureFlagIsOff() throws Exception {
        Long topicId = createTopic("ai-provider-disabled-topic-");

        // POST возвращает pending; provider заблокирован feature flag — ждём failed
        String created = mockMvc.perform(post("/api/admin/ai/questions/generate")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "questionType": "SCQ",
                                  "difficulty": 2,
                                  "count": 1,
                                  "languageMode": "RU_KK",
                                  "instruction": "No external calls"
                                }
                                """.formatted(topicId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long jobId = objectMapper.readTree(created).get("id").asLong();
        JsonNode finished = awaitTerminalStatus(jobId);

        org.assertj.core.api.Assertions.assertThat(finished.get("status").asText()).isEqualTo("failed");
        org.assertj.core.api.Assertions.assertThat(finished.get("errorCode").asText()).isEqualTo("ai_provider_disabled");
    }

    private JsonNode awaitTerminalStatus(Long jobId) throws Exception {
        for (int i = 0; i < 50; i++) {
            String body = mockMvc.perform(get("/api/admin/ai/jobs/{jobId}", jobId)
                            .with(user("admin@damulab.kz").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode job = objectMapper.readTree(body);
            String status = job.get("status").asText();
            if (!status.equals("pending") && !status.equals("running")) {
                return job;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("AI job " + jobId + " не перешёл в терминальный статус за 5 секунд");
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
                                  "titleRu": "AI provider topic %s",
                                  "titleKk": "AI provider topic %s"
                                }
                                """.formatted(subjectId, gradeId, prefix, suffix, suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int start = response.indexOf("\"id\":") + 5;
        int end = response.indexOf(',', start);
        return Long.valueOf(response.substring(start, end));
    }
}
