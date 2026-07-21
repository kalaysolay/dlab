package kz.damulab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.ArrayList;
import java.util.List;
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

import kz.damulab.ai.AiGenerationJobRepository;
import kz.damulab.content.GradeRepository;
import kz.damulab.content.SubjectRepository;
import kz.damulab.content.TopicAiExampleException;
import kz.damulab.content.TopicAiExampleForm;
import kz.damulab.content.TopicAiExampleResponse;
import kz.damulab.content.TopicAiExampleService;
import kz.damulab.questions.ChoiceOptionForm;
import kz.damulab.questions.FillAnswerForm;
import kz.damulab.questions.FillMatchMode;
import kz.damulab.questions.MatchingPairForm;
import kz.damulab.questions.QuestionType;

/**
 * Интеграционные тесты фичи «Эталоны для ИИ» (вариант B):
 * сервис (CRUD, лимит, валидация ключа под тип), страницы админки, REST активных эталонов
 * и попадание эталона в outbound-payload генерации (без внутренней заметки).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TopicAiExampleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TopicAiExampleService exampleService;

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private GradeRepository grades;

    @Autowired
    private AiGenerationJobRepository jobs;

    @Autowired
    private ObjectMapper objectMapper;

    // ───── Сервис: CRUD и типы ключей ─────────────────────────────────────────

    @Test
    void serviceCreatesAndReadsScqExample() throws Exception {
        Long topicId = createTopic("ai-ex-scq-");
        TopicAiExampleResponse created = exampleService.create(topicId, scqForm());

        List<TopicAiExampleResponse> list = exampleService.listByTopic(topicId);
        assertThat(list).hasSize(1);
        assertThat(created.questionType()).isEqualTo("SCQ");
        assertThat(created.options()).hasSize(4);
        assertThat(created.options().stream().filter(ChoiceOptionForm::isCorrect).count()).isEqualTo(1);
    }

    @Test
    void serviceStoresMatchingAndFillKeys() throws Exception {
        Long topicId = createTopic("ai-ex-multi-");
        TopicAiExampleResponse matching = exampleService.create(topicId, matchingForm());
        TopicAiExampleResponse fill = exampleService.create(topicId, fillForm());

        assertThat(matching.matchingPairs()).hasSize(2);
        assertThat(fill.fillAnswers()).hasSize(1);
        assertThat(fill.fillAnswers().get(0).getPlaceholder()).isEqualTo("[[1]]");
    }

    // ───── Сервис: лимит хранения ──────────────────────────────────────────────

    @Test
    void serviceEnforcesStoreLimitPerTopic() throws Exception {
        Long topicId = createTopic("ai-ex-limit-");
        for (int i = 0; i < TopicAiExampleService.MAX_EXAMPLES_PER_TOPIC; i++) {
            exampleService.create(topicId, scqForm());
        }
        assertThatThrownBy(() -> exampleService.create(topicId, scqForm()))
                .isInstanceOf(TopicAiExampleException.class)
                .hasMessage("example_limit_reached");
    }

    // ───── Сервис: валидация ключа под тип ──────────────────────────────────────

    @Test
    void serviceRejectsScqWithoutExactlyOneCorrect() throws Exception {
        Long topicId = createTopic("ai-ex-badscq-");
        TopicAiExampleForm form = scqForm();
        form.getOptions().forEach(o -> o.setCorrect(false)); // ни одного правильного
        assertThatThrownBy(() -> exampleService.create(topicId, form))
                .isInstanceOf(TopicAiExampleException.class)
                .hasMessage("scq_requires_exactly_one_correct");
    }

    @Test
    void serviceRejectsMatchingWithSinglePair() throws Exception {
        Long topicId = createTopic("ai-ex-badmatch-");
        TopicAiExampleForm form = matchingForm();
        form.setMatchingPairs(new ArrayList<>(List.of(new MatchingPairForm("A", "A", "1", "1"))));
        assertThatThrownBy(() -> exampleService.create(topicId, form))
                .isInstanceOf(TopicAiExampleException.class)
                .hasMessage("matching_requires_two_pairs");
    }

    @Test
    void serviceRejectsFillWithoutAnswer() throws Exception {
        Long topicId = createTopic("ai-ex-badfill-");
        TopicAiExampleForm form = fillForm();
        form.setFillAnswers(new ArrayList<>());
        assertThatThrownBy(() -> exampleService.create(topicId, form))
                .isInstanceOf(TopicAiExampleException.class)
                .hasMessage("fill_in_requires_answer");
    }

    // ───── Покрытие для хаба ────────────────────────────────────────────────────

    @Test
    void serviceCoverageCountsActiveTypes() throws Exception {
        Long topicId = createTopic("ai-ex-cover-");
        exampleService.create(topicId, scqForm());
        TopicAiExampleForm inactive = matchingForm();
        inactive.setIncludeInAi(false);
        exampleService.create(topicId, inactive);

        Long subjectId = mathSubjectId();
        Long gradeId = grade4Id();
        var row = exampleService.coverage(subjectId, gradeId).stream()
                .filter(c -> c.topicId().equals(topicId))
                .findFirst()
                .orElseThrow();
        assertThat(row.total()).isEqualTo(2);
        assertThat(row.active()).isEqualTo(1);
        assertThat(row.typesActive()).containsExactly("SCQ");
    }

    // ───── Страницы ─────────────────────────────────────────────────────────────

    @Test
    void hubPageIsServerRendered() throws Exception {
        mockMvc.perform(get("/admin/ai-examples").with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/ai-examples"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Эталоны для ИИ")));
    }

    @Test
    void topicExamplesPageRendersCreatedExample() throws Exception {
        Long topicId = createTopic("ai-ex-page-");
        exampleService.create(topicId, scqForm());
        mockMvc.perform(get("/admin/topics/{id}/ai-examples", topicId)
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/topic-ai-examples"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Сколько будет 2 + 2")));
    }

    @Test
    void createFormRendersAndSaveRedirects() throws Exception {
        Long topicId = createTopic("ai-ex-create-");
        mockMvc.perform(get("/admin/topics/{id}/ai-examples/new", topicId)
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/topic-ai-example-form"));

        // POST формы SCQ -> редирект на список
        mockMvc.perform(post("/admin/topics/{id}/ai-examples", topicId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .param("questionType", "SCQ")
                        .param("difficulty", "2")
                        .param("bodyRu", "Сколько будет 3 + 1?")
                        .param("bodyKk", "3 + 1 неше?")
                        .param("includeInAi", "true")
                        .param("options[0].label", "A").param("options[0].textRu", "4").param("options[0].textKk", "4").param("options[0].correct", "true")
                        .param("options[1].label", "B").param("options[1].textRu", "5").param("options[1].textKk", "5")
                        .param("options[2].label", "C").param("options[2].textRu", "3").param("options[2].textKk", "3"))
                .andExpect(status().is3xxRedirection());

        assertThat(exampleService.listByTopic(topicId)).hasSize(1);
    }

    // ───── REST активных эталонов ───────────────────────────────────────────────

    @Test
    void apiReturnsOnlyActiveExamples() throws Exception {
        Long topicId = createTopic("ai-ex-api-");
        exampleService.create(topicId, scqForm());
        TopicAiExampleForm inactive = scqForm();
        inactive.setIncludeInAi(false);
        exampleService.create(topicId, inactive);

        mockMvc.perform(get("/api/admin/topics/{id}/ai-examples", topicId)
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].includeInAi").value(true));
    }

    // ───── Попадание эталона в payload генерации ────────────────────────────────

    @Test
    void generationPayloadIncludesExampleBodyButNotInternalNote() throws Exception {
        Long topicId = createTopic("ai-ex-payload-");
        TopicAiExampleForm form = scqForm();
        form.setBodyRu("ЭТАЛОН_ТЕЛО_МАРКЕР");
        form.setInternalNote("СЕКРЕТНАЯ_ЗАМЕТКА_МЕТОДИСТА");
        exampleService.create(topicId, form);

        JsonNode job = performGeneration(topicId, "обычная генерация");
        String payload = jobs.findById(job.get("id").asLong()).orElseThrow().getRequestPayloadJson();

        // Эталон уходит в промпт как few-shot: тело есть в payload.
        assertThat(payload).contains("ЭТАЛОН_ТЕЛО_МАРКЕР");
        // Внутренняя заметка методиста НЕ должна покидать систему.
        assertThat(payload).doesNotContain("СЕКРЕТНАЯ_ЗАМЕТКА_МЕТОДИСТА");
        assertThat(payload).doesNotContain("internalNote");
    }

    // ───── Хелперы форм ─────────────────────────────────────────────────────────

    private TopicAiExampleForm scqForm() {
        TopicAiExampleForm form = new TopicAiExampleForm();
        form.setQuestionType(QuestionType.SCQ);
        form.setDifficulty(2);
        form.setBodyRu("Сколько будет 2 + 2?");
        form.setBodyKk("2 + 2 неше?");
        List<ChoiceOptionForm> options = new ArrayList<>();
        options.add(new ChoiceOptionForm("A", "4", "4", true));
        options.add(new ChoiceOptionForm("B", "5", "5", false));
        options.add(new ChoiceOptionForm("C", "3", "3", false));
        options.add(new ChoiceOptionForm("D", "22", "22", false));
        form.setOptions(options);
        return form;
    }

    private TopicAiExampleForm matchingForm() {
        TopicAiExampleForm form = new TopicAiExampleForm();
        form.setQuestionType(QuestionType.MATCHING);
        form.setDifficulty(3);
        form.setBodyRu("Сопоставьте числа и слова");
        form.setBodyKk("Сандар мен сөздерді сәйкестендір");
        List<MatchingPairForm> pairs = new ArrayList<>();
        pairs.add(new MatchingPairForm("1", "1", "один", "бір"));
        pairs.add(new MatchingPairForm("2", "2", "два", "екі"));
        form.setMatchingPairs(pairs);
        return form;
    }

    private TopicAiExampleForm fillForm() {
        TopicAiExampleForm form = new TopicAiExampleForm();
        form.setQuestionType(QuestionType.FILL_IN);
        form.setDifficulty(2);
        form.setBodyRu("2 + 2 = [[1]]");
        form.setBodyKk("2 + 2 = [[1]]");
        List<FillAnswerForm> fills = new ArrayList<>();
        fills.add(new FillAnswerForm("[[1]]", "4", FillMatchMode.EXACT, null));
        form.setFillAnswers(fills);
        return form;
    }

    // ───── Хелперы генерации/тем (как в AiContentFactoryIntegrationTest) ─────────

    private JsonNode performGeneration(Long topicId, String instruction) throws Exception {
        String created = mockMvc.perform(post("/api/admin/ai/questions/generate")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "questionType": "SCQ",
                                  "difficulty": 2,
                                  "count": 3,
                                  "languageMode": "RU_KK",
                                  "instruction": "%s"
                                }
                                """.formatted(topicId, instruction)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long jobId = objectMapper.readTree(created).get("id").asLong();
        for (int i = 0; i < 50; i++) {
            String body = mockMvc.perform(get("/api/admin/ai/jobs/{jobId}", jobId)
                            .with(user("admin@damulab.kz").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode job = objectMapper.readTree(body);
            String s = job.get("status").asText();
            if (!s.equals("pending") && !s.equals("running")) {
                return job;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("AI job " + jobId + " не перешёл в терминальный статус");
    }

    private Long createTopic(String prefix) throws Exception {
        Long subjectId = mathSubjectId();
        Long gradeId = grade4Id();
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
                                  "titleRu": "AI example topic %s",
                                  "titleKk": "AI example topic %s"
                                }
                                """.formatted(subjectId, gradeId, prefix, suffix, suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long mathSubjectId() {
        return subjects.findAllByOrderByTitleRuAsc().stream()
                .filter(subject -> "math".equals(subject.getCode()))
                .findFirst().orElseThrow().getId();
    }

    private Long grade4Id() {
        return grades.findAllByOrderByGradeNoAsc().stream()
                .filter(grade -> Integer.valueOf(4).equals(grade.getGradeNo()))
                .findFirst().orElseThrow().getId();
    }
}
