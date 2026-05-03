package kz.damulab;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
class MiniLectureAiProviderDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private GradeRepository grades;

    @Test
    void miniLectureGenerateReturnsServiceUnavailableWhenRealProvidersOff() throws Exception {
        TopicIds ids = createTopic("mini-lecture-ai-disabled-");

        mockMvc.perform(post("/api/admin/questions/mini-lecture/generate")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": %d,
                                  "topicIds": [%d],
                                  "gradeIds": [%d],
                                  "type": "SCQ",
                                  "difficulty": 2,
                                  "bodyRu": "Найдите 20%% от 350",
                                  "bodyKk": "350 санының 20 пайызын табыңыз",
                                  "source": "Ручной ввод",
                                  "options": [
                                    {"label":"A","textRu":"60","textKk":"60","correct":false},
                                    {"label":"B","textRu":"70","textKk":"70","correct":true},
                                    {"label":"C","textRu":"75","textKk":"75","correct":false}
                                  ]
                                }
                                """.formatted(ids.subjectId(), ids.topicId(), ids.gradeId())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("ai_provider_disabled"));
    }

    private record TopicIds(long topicId, long subjectId, long gradeId) {
    }

    private TopicIds createTopic(String prefix) throws Exception {
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
                                  "titleRu": "Mini lecture AI topic %s",
                                  "titleKk": "Mini lecture AI topic %s"
                                }
                                """.formatted(subjectId, gradeId, prefix, suffix, suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int start = response.indexOf("\"id\":") + 5;
        int end = response.indexOf(',', start);
        long topicId = Long.parseLong(response.substring(start, end));
        return new TopicIds(topicId, subjectId, gradeId);
    }
}
