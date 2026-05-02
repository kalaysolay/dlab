package kz.damulab;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import kz.damulab.audit.AdminContentAuditLogRepository;
import kz.damulab.content.GradeRepository;
import kz.damulab.content.SubjectRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuestionBankIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private GradeRepository grades;

    @Autowired
    private AdminContentAuditLogRepository auditLogs;

    @Test
    void adminCanCreateScqQuestionAndApproveIt() throws Exception {
        Long topicId = createTopic("scq-topic-");

        String response = mockMvc.perform(post("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scqBody(topicId, "Найдите 20% от 350", true)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/admin/questions/")))
                .andExpect(jsonPath("$.type").value("SCQ"))
                .andExpect(jsonPath("$.status").value("draft"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long questionId = idFrom(response);

        mockMvc.perform(post("/api/admin/questions/{id}/approve", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));

        org.assertj.core.api.Assertions.assertThat(auditLogs.countByEntityTypeAndEntityId("Question", questionId))
                .isGreaterThan(0);
    }

    @Test
    void draftQuestionCannotBePublishedWithoutApproval() throws Exception {
        Long topicId = createTopic("draft-publish-topic-");
        Long questionId = idFrom(mockMvc.perform(post("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scqBody(topicId, "Find 20% of 350", true)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        mockMvc.perform(post("/api/admin/questions/{id}/publish", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("question_not_approved"));
    }

    @Test
    void scqRequiresExactlyOneCorrectAnswer() throws Exception {
        Long topicId = createTopic("bad-scq-topic-");

        mockMvc.perform(post("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scqBody(topicId, "Найдите 10% от 100", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("scq_requires_exactly_one_correct"));
    }

    @Test
    void softDeletedChoiceOptionsAreIgnored() throws Exception {
        Long topicId = createTopic("soft-delete-choice-topic-");

        mockMvc.perform(post("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "type": "SCQ",
                                  "difficulty": 2,
                                  "bodyRu": "РќР°Р№РґРёС‚Рµ 20%% РѕС‚ 350",
                                  "bodyKk": "350 СЃР°РЅС‹РЅС‹ТЈ 20 РїР°Р№С‹Р·С‹РЅ С‚Р°Р±С‹ТЈС‹Р·",
                                  "source": "Р СѓС‡РЅРѕР№ РІРІРѕРґ",
                                  "options": [
                                    {"label":"A","textRu":"60","textKk":"60","correct":false},
                                    {"label":"B","textRu":"70","textKk":"70","correct":true},
                                    {"label":"C","textRu":"75","textKk":"75","correct":true,"soft_delete":true}
                                  ]
                                }
                                """.formatted(topicId)))
                .andExpect(status().isCreated());
    }

    @Test
    void mcqRequiresAtLeastOneCorrectAnswer() throws Exception {
        Long topicId = createTopic("mcq-topic-");

        mockMvc.perform(post("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "type": "MCQ",
                                  "difficulty": 2,
                                  "bodyRu": "Выберите верные утверждения",
                                  "bodyKk": "Дұрыс тұжырымдарды таңдаңыз",
                                  "source": "Ручной ввод",
                                  "options": [
                                    {"label":"A","textRu":"25%% = 1/5","textKk":"25%% = 1/5","correct":false},
                                    {"label":"B","textRu":"50%% = 1/3","textKk":"50%% = 1/3","correct":false}
                                  ]
                                }
                                """.formatted(topicId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("mcq_requires_one_correct"));
    }

    @Test
    void matchingAndFillInQuestionsCanBeCreated() throws Exception {
        Long topicId = createTopic("matching-fill-topic-");

        mockMvc.perform(post("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "type": "MATCHING",
                                  "difficulty": 3,
                                  "bodyRu": "Соотнесите проценты и дроби",
                                  "bodyKk": "Пайыздар мен бөлшектерді сәйкестендіріңіз",
                                  "source": "Ручной ввод",
                                  "matchingPairs": [
                                    {"leftRu":"50%%","leftKk":"50%%","rightRu":"0.5","rightKk":"0.5"},
                                    {"leftRu":"25%%","leftKk":"25%%","rightRu":"0.25","rightKk":"0.25"}
                                  ]
                                }
                                """.formatted(topicId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("MATCHING"));

        mockMvc.perform(post("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "type": "FILL_IN",
                                  "difficulty": 2,
                                  "bodyRu": "15%% от 200 равно [[1]]",
                                  "bodyKk": "200 санының 15 пайызы [[1]]",
                                  "source": "Ручной ввод",
                                  "fillAnswers": [
                                    {"placeholder":"[[1]]","answer":"30","matchMode":"NUMERIC_TOLERANCE","tolerance":0.01}
                                  ]
                                }
                                """.formatted(topicId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("FILL_IN"));
    }

    @Test
    void adminCanGenerateMiniLectureDraftForQuestionForm() throws Exception {
        Long topicId = createTopic("mini-lecture-topic-");

        mockMvc.perform(post("/api/admin/questions/mini-lecture/generate")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
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
                                """.formatted(topicId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctAnswerRu").value(containsString("B")))
                .andExpect(jsonPath("$.correctAnswerRu").value(containsString("70")))
                .andExpect(jsonPath("$.explanationRu").value(containsString("Правильный ответ")))
                .andExpect(jsonPath("$.miniLectureRu").value(containsString("Объяснение для школьника")))
                .andExpect(jsonPath("$.miniLectureKk").value(containsString("Мектеп оқушысына")));
    }

    @Test
    void publishedQuestionEditKeepsPublishedVersionLive() throws Exception {
        Long topicId = createTopic("version-topic-");
        Long questionId = idFrom(mockMvc.perform(post("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scqBody(topicId, "Найдите 20% от 350", true)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        mockMvc.perform(post("/api/admin/questions/{id}/approve", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/questions/{id}/publish", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("published"));

        mockMvc.perform(patch("/api/admin/questions/{id}", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scqBody(topicId, "Найдите 25% от 400", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.status").value("published"))
                .andExpect(jsonPath("$.bodyRu").value("Найдите 20% от 350"));
    }

    @Test
    void topicWithQuestionCannotBeDeleted() throws Exception {
        Long topicId = createTopic("question-blocks-topic-");
        mockMvc.perform(post("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scqBody(topicId, "Найдите 5% от 200", true)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/admin/topics/{id}", topicId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("topic_has_questions"));
    }

    @Test
    void adminQuestionPagesAreServerRendered() throws Exception {
        Long topicId = createTopic("question-edit-page-topic-");
        Long questionId = idFrom(mockMvc.perform(post("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scqBody(topicId, "Вопрос для edit page", true)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        mockMvc.perform(get("/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/questions"))
                .andExpect(content().string(containsString("Банк вопросов")));

        mockMvc.perform(get("/admin/questions/new")
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/question-form"))
                .andExpect(content().string(containsString("Добавить вопрос")));

        mockMvc.perform(get("/admin/questions/{id}/edit", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/question-form"))
                .andExpect(content().string(containsString("Редактировать вопрос")))
                .andExpect(content().string(containsString("Q-" + questionId)));
    }

    @Test
    void adminCanSubmitQuestionEditPage() throws Exception {
        Long topicId = createTopic("question-edit-submit-topic-");
        Long questionId = idFrom(mockMvc.perform(post("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scqBody(topicId, "Исходный текст", true)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        mockMvc.perform(post("/admin/questions/{id}", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .param("topicId", String.valueOf(topicId))
                        .param("type", "SCQ")
                        .param("difficulty", "2")
                        .param("bodyRu", "Обновленный текст")
                        .param("bodyKk", "Жаңартылған мәтін")
                        .param("source", "Ручной ввод")
                        .param("status", "DRAFT")
                        .param("options[0].label", "A")
                        .param("options[0].textRu", "60")
                        .param("options[0].textKk", "60")
                        .param("options[0].correct", "false")
                        .param("options[0].softDeleted", "false")
                        .param("options[1].label", "B")
                        .param("options[1].textRu", "70")
                        .param("options[1].textKk", "70")
                        .param("options[1].correct", "true")
                        .param("options[1].softDeleted", "false")
                        .param("options[2].label", "C")
                        .param("options[2].textRu", "80")
                        .param("options[2].textKk", "80")
                        .param("options[2].correct", "false")
                        .param("options[2].softDeleted", "false")
                        .param("options[3].label", "D")
                        .param("options[3].textRu", "90")
                        .param("options[3].textKk", "90")
                        .param("options[3].correct", "false")
                        .param("options[3].softDeleted", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/questions"));

        mockMvc.perform(get("/api/admin/questions/{id}", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bodyRu").value("Обновленный текст"));
    }

    @Test
    void studentCannotOpenAdminQuestionsApi() throws Exception {
        mockMvc.perform(get("/api/admin/questions")
                        .with(user("student@damulab.kz").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    private String scqBody(Long topicId, String bodyRu, boolean exactlyOneCorrect) {
        String correctA = exactlyOneCorrect ? "false" : "true";
        return """
                {
                  "topicId": %d,
                  "type": "SCQ",
                  "difficulty": 2,
                  "bodyRu": "%s",
                  "bodyKk": "350 санының 20 пайызын табыңыз",
                  "source": "Ручной ввод",
                  "options": [
                    {"label":"A","textRu":"60","textKk":"60","correct":%s},
                    {"label":"B","textRu":"70","textKk":"70","correct":true},
                    {"label":"C","textRu":"75","textKk":"75","correct":false}
                  ]
                }
                """.formatted(topicId, bodyRu, correctA);
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
                                  "titleRu": "Question topic %s",
                                  "titleKk": "Question topic %s"
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
