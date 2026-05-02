package kz.damulab;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import kz.damulab.content.GradeRepository;
import kz.damulab.content.SubjectRepository;
import kz.damulab.questions.QuestionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuestionImportHealthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private GradeRepository grades;

    @Autowired
    private QuestionRepository questions;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanImportJsonQuestionsIntoNeedsReview() throws Exception {
        Long topicId = createTopic("import-topic-");
        String marker = "JSON import " + UUID.randomUUID();

        mockMvc.perform(post("/api/admin/question-imports")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importBody(topicId, marker, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.importedRows").value(1))
                .andExpect(jsonPath("$.errorRows").value(0));

        mockMvc.perform(get("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .param("query", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("needs_review"))
                .andExpect(jsonPath("$[0].source").value(marker));
    }

    @Test
    void invalidImportRowCreatesRowErrorWithoutCreatingQuestion() throws Exception {
        Long topicId = createTopic("bad-import-topic-");
        long before = questions.count();

        mockMvc.perform(post("/api/admin/question-imports")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importBody(topicId, "bad import " + UUID.randomUUID(), false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.importedRows").value(0))
                .andExpect(jsonPath("$.errorRows").value(1))
                .andExpect(jsonPath("$.errors[0].rowNo").value(1))
                .andExpect(jsonPath("$.errors[0].errorCode").value("scq_requires_exactly_one_correct"));

        org.assertj.core.api.Assertions.assertThat(questions.count()).isEqualTo(before);
    }

    @Test
    void healthEndpointAndFlagActionExposeProblemReviewFlow() throws Exception {
        Long topicId = createTopic("health-topic-");
        String marker = "Health marker " + UUID.randomUUID();
        String response = mockMvc.perform(post("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scqBody(topicId, marker)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long questionId = objectMapper.readTree(response).path("id").asLong();

        mockMvc.perform(get("/api/admin/questions/health")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .param("quality", "NO_ATTEMPTS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noAttemptQuestions", greaterThanOrEqualTo(1)))
                .andExpect(content().string(containsString(marker)));

        mockMvc.perform(post("/api/admin/questions/{id}/flag", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"content_health\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("needs_review"));

        mockMvc.perform(post("/api/admin/questions/{id}/flags", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"COMPLAINT\",\"reason\":\"Текст вопроса спорный\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("complaint"))
                .andExpect(jsonPath("$.status").value("open"));
    }

    @Test
    void importPageIsServerRendered() throws Exception {
        mockMvc.perform(get("/admin/questions/import")
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/question-import"))
                .andExpect(content().string(containsString("Импорт вопросов")));
    }

    @Test
    void adminCanImportExcelQuestions() throws Exception {
        Long topicId = createTopic("excel-import-topic-");
        String source = "Excel import " + UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes(topicId, source)
        );

        mockMvc.perform(multipart("/api/admin/question-imports/excel")
                        .file(file)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.sourceType").value("XLSX"))
                .andExpect(jsonPath("$.originalFilename").value("questions.xlsx"))
                .andExpect(jsonPath("$.importedRows").value(1));

        mockMvc.perform(get("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .param("query", source))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("needs_review"));
    }

    @Test
    void healthPageIsServerRendered() throws Exception {
        mockMvc.perform(get("/admin/questions/health")
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/question-health"))
                .andExpect(content().string(containsString("Качество вопросов")));
    }

    private String importBody(Long topicId, String source, boolean valid) {
        String correctA = valid ? "false" : "true";
        return """
                {
                  "questions": [
                    {
                      "topicId": %d,
                      "type": "SCQ",
                      "difficulty": 2,
                      "bodyRu": "Импортный вопрос",
                      "bodyKk": "Импорт сұрағы",
                      "source": "%s",
                      "status": "DRAFT",
                      "options": [
                        {"label":"A","textRu":"10","textKk":"10","correct":%s},
                        {"label":"B","textRu":"20","textKk":"20","correct":true},
                        {"label":"C","textRu":"30","textKk":"30","correct":false}
                      ]
                    }
                  ]
                }
                """.formatted(topicId, source, correctA);
    }

    private String scqBody(Long topicId, String bodyRu) {
        return """
                {
                  "topicId": %d,
                  "type": "SCQ",
                  "difficulty": 2,
                  "bodyRu": "%s",
                  "bodyKk": "Health сұрағы",
                  "source": "Health test",
                  "options": [
                    {"label":"A","textRu":"10","textKk":"10","correct":false},
                    {"label":"B","textRu":"20","textKk":"20","correct":true},
                    {"label":"C","textRu":"30","textKk":"30","correct":false}
                  ]
                }
                """.formatted(topicId, bodyRu);
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
                                  "titleRu": "Import topic %s",
                                  "titleKk": "Import topic %s"
                                }
                                """.formatted(subjectId, gradeId, prefix, suffix, suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.path("id").asLong();
    }

    private byte[] excelBytes(Long topicId, String source) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("questions");
            Row header = sheet.createRow(0);
            String[] headers = {"type", "topicId", "difficulty", "bodyRu", "bodyKk", "source", "payload", "correct", "explanationRu", "explanationKk"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("SCQ");
            row.createCell(1).setCellValue(topicId);
            row.createCell(2).setCellValue(2);
            row.createCell(3).setCellValue("Excel вопрос");
            row.createCell(4).setCellValue("Excel сұрағы");
            row.createCell(5).setCellValue(source);
            row.createCell(6).setCellValue("A|10|10;B|20|20;C|30|30");
            row.createCell(7).setCellValue("B");
            row.createCell(8).setCellValue("10% от 200 = 20");
            row.createCell(9).setCellValue("200 санының 10 пайызы 20");
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
