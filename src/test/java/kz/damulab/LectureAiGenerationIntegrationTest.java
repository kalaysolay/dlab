package kz.damulab;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import kz.damulab.ai.AiProviderCode;
import kz.damulab.ai.AiRuntimeSetting;
import kz.damulab.ai.AiRuntimeSettingRepository;
import kz.damulab.ai.AiUsageType;
import kz.damulab.content.TopicRepository;

/** Проверяет пользовательский сценарий кнопки: topicId -> RU/KZ HTML -> отчёт не ниже 95. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LectureAiGenerationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TopicRepository topics;

    @Autowired
    private AiRuntimeSettingRepository settings;

    @BeforeEach
    void useDeterministicProvider() {
        AiRuntimeSetting lectures = settings.findById(AiUsageType.LECTURES).orElseThrow();
        lectures.update(AiProviderCode.STUB, "stub", "test");
        settings.save(lectures);
    }

    @Test
    void createPageContainsGeneratorAndAcceptedDraftHasKatexAndBothLanguages() throws Exception {
        Long topicId = topics.findAll().stream().filter(topic -> !topic.isDeleted()).findFirst().orElseThrow().getId();

        mockMvc.perform(get("/admin/lectures/new")
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Сгенерировать лекцию RU + KZ")))
                .andExpect(content().string(containsString("Отчёт валидатора")));

        mockMvc.perform(post("/api/admin/lectures/generate")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topicId": %d, "methodistInstruction": "Добавь самопроверку"}
                                """.formatted(topicId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titleRu").isNotEmpty())
                .andExpect(jsonPath("$.titleKk").isNotEmpty())
                .andExpect(jsonPath("$.contentRu", containsString("class=\"ql-formula\"")))
                .andExpect(jsonPath("$.contentKk", containsString("class=\"ql-formula\"")))
                .andExpect(jsonPath("$.quality.score", greaterThanOrEqualTo(95)))
                .andExpect(jsonPath("$.quality.minimumScore").value(95));
    }

    @Test
    void generationRequiresTopicAndAdminRole() throws Exception {
        mockMvc.perform(post("/api/admin/lectures/generate")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/lectures/generate")
                        .with(user("student@damulab.kz").roles("STUDENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":1}"))
                .andExpect(status().isForbidden());
    }

}
