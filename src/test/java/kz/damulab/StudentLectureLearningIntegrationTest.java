package kz.damulab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import kz.damulab.content.GradeRepository;
import kz.damulab.content.Subject;
import kz.damulab.content.SubjectRepository;
import kz.damulab.lectures.StudentLectureProgress;
import kz.damulab.lectures.StudentLectureProgressRepository;
import kz.damulab.lectures.StudentLectureStatus;
import kz.damulab.users.AppUser;
import kz.damulab.users.AppUserRepository;
import kz.damulab.users.StudentProfile;
import kz.damulab.users.StudentProfileRepository;

/**
 * Интеграционные сценарии ученического каталога, прогресса и контрольных вопросов.
 * Административные HTTP-методы используются для подготовки реальных опубликованных данных.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentLectureLearningIntegrationTest {

    private static final String STUDENT_EMAIL = "student@damulab.kz";

    /** Идентификаторы предмета, класса и созданной темы для одного теста. */
    private record TopicFixture(long topicId, long subjectId, long gradeId) {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private GradeRepository grades;

    @Autowired
    private StudentProfileRepository students;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private StudentLectureProgressRepository progressRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Проверяет, что ссылка на уроки расположена между миссией и последним занятием. */
    @Test
    void dashboardShowsLessonsLinkInRequestedPosition() throws Exception {
        String dashboard = mockMvc.perform(get("/student")
                        .with(user(STUDENT_EMAIL).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("student-lessons-link")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        int missionPosition = dashboard.indexOf("student-home-grid-top");
        int lessonsPosition = dashboard.indexOf("student-lessons-link");
        int lastLessonPosition = dashboard.indexOf("student-last-card");
        assertThat(lessonsPosition).isGreaterThan(missionPosition);
        assertThat(lessonsPosition).isLessThan(lastLessonPosition);
    }

    /** Проверяет каталог по предмету и классу, сортировку и начальный статус лекции. */
    @Test
    void catalogShowsSubjectTilesAndNewestLectureFirst() throws Exception {
        TopicFixture fixture = createTopic("student-catalog-");
        String marker = shortMarker();
        Long olderLectureId = createAndPublishLecture(fixture.topicId(), "Старый урок " + marker, null);
        Long newerLectureId = createAndPublishLecture(fixture.topicId(), "Новый урок " + marker, null);

        mockMvc.perform(get("/student/lectures")
                        .with(user(STUDENT_EMAIL).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("student/lectures"))
                .andExpect(content().string(containsString("lecture-subject-grid")))
                .andExpect(content().string(containsString("Математика")));

        String subjectPage = mockMvc.perform(get(
                            "/student/lectures/subjects/{subjectId}/grades/{gradeId}",
                            fixture.subjectId(),
                            fixture.gradeId()
                    )
                        .with(user(STUDENT_EMAIL).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("student/lecture-subject"))
                .andExpect(content().string(containsString("Не начата")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Второй созданный урок должен находиться раньше первого, потому что сортировка
        // основана на created_at по убыванию.
        assertThat(subjectPage.indexOf("Новый урок " + marker))
                .isLessThan(subjectPage.indexOf("Старый урок " + marker));
        assertThat(olderLectureId).isNotEqualTo(newerLectureId);
    }

    /**
     * Проверяет главное правило каталога понятным пользовательским сценарием:
     * один предмет получает отдельные плитки для классов с опубликованными уроками,
     * класс только с черновиком не показывается, а страницы классов не смешиваются.
     */
    @Test
    void catalogSeparatesGradesAndHidesDraftOnlySection() throws Exception {
        String marker = shortMarker();
        String subjectTitle = "Предмет каталога " + marker;
        Subject subject = subjects.save(new Subject(
                "lecture-catalog-" + marker,
                subjectTitle,
                "Каталог пәні " + marker,
                "Предмет для проверки каталога уроков",
                "Сабақтар каталогын тексеруге арналған пән"
        ));
        TopicFixture gradeThree = createTopic("student-grade-three-", subject.getId(), 3);
        TopicFixture gradeFour = createTopic("student-grade-four-", subject.getId(), 4);
        TopicFixture gradeFiveDraft = createTopic("student-grade-five-", subject.getId(), 5);
        String gradeThreeTitle = "Урок третьего класса " + marker;
        String gradeFourTitle = "Урок четвёртого класса " + marker;

        Long gradeThreeLectureId = createAndPublishLecture(gradeThree.topicId(), gradeThreeTitle, null);
        createAndPublishLecture(gradeFour.topicId(), gradeFourTitle, null);
        createLecture(gradeFiveDraft.topicId(), "Черновик пятого класса " + marker, null);

        String catalog = mockMvc.perform(get("/student/lectures")
                        .with(user(STUDENT_EMAIL).roles("STUDENT")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(catalog)
                .contains(subjectTitle + " · 3 класс")
                .contains(subjectTitle + " · 4 класс")
                .doesNotContain(subjectTitle + " · 5 класс");

        String gradeThreePage = mockMvc.perform(get(
                            "/student/lectures/subjects/{subjectId}/grades/{gradeId}",
                            subject.getId(),
                            gradeThree.gradeId()
                    )
                        .with(user(STUDENT_EMAIL).roles("STUDENT")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(gradeThreePage)
                .contains(gradeThreeTitle)
                .doesNotContain(gradeFourTitle);

        // Из самого урока кнопка «Назад» должна вести в тот же класс, а не в общий предмет.
        mockMvc.perform(get("/student/lectures/{id}", gradeThreeLectureId)
                        .with(user(STUDENT_EMAIL).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "/student/lectures/subjects/" + subject.getId()
                                + "/grades/" + gradeThree.gradeId()
                )));
    }

    /** Проверяет простой путь NOT_STARTED → IN_PROGRESS → DONE без теста. */
    @Test
    void lectureWithoutCheckpointsCanBeCompletedImmediately() throws Exception {
        TopicFixture fixture = createTopic("student-simple-progress-");
        Long lectureId = createAndPublishLecture(fixture.topicId(), "Урок без теста " + shortMarker(), null);

        mockMvc.perform(get("/student/lectures/{id}", lectureId)
                        .with(user(STUDENT_EMAIL).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("student/lecture"))
                .andExpect(content().string(containsString("/webjars/katex/")))
                .andExpect(content().string(containsString("/dist/katex.min.css")))
                .andExpect(content().string(containsString("/dist/katex.min.js")))
                .andExpect(content().string(containsString("window.katex.render")))
                .andExpect(content().string(containsString("data-lesson-tab=\"theory\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("data-lesson-tab=\"testing\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("data-open-lesson-tab=\"testing\""))))
                .andExpect(content().string(containsString("В этом уроке нет тестирования")))
                .andExpect(content().string(containsString("После изучения теории можно завершить урок и вернуться к списку уроков.")))
                .andExpect(content().string(containsString("Завершить урок")));

        assertThat(progress(lectureId).getStatus()).isEqualTo(StudentLectureStatus.IN_PROGRESS);

        mockMvc.perform(post("/student/lectures/{id}/complete", lectureId)
                        .with(user(STUDENT_EMAIL).roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/lectures/" + lectureId));

        assertThat(progress(lectureId).getStatus()).isEqualTo(StudentLectureStatus.DONE);
        assertThat(progress(lectureId).getCompletedAt()).isNotNull();
        var completedAt = progress(lectureId).getCompletedAt();

        // Повторный POST безопасен: завершённый урок не сбрасывается и дата не переписывается.
        mockMvc.perform(post("/student/lectures/{id}/complete", lectureId)
                        .with(user(STUDENT_EMAIL).roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(progress(lectureId).getStatus()).isEqualTo(StudentLectureStatus.DONE);
        assertThat(progress(lectureId).getCompletedAt()).isEqualTo(completedAt);
    }

    /** Проверяет выбор содержимого глобальной локалью и отсутствие языковых вкладок в reader. */
    @Test
    void readerUsesApplicationLocaleAndFallsBackToRussianContent() throws Exception {
        TopicFixture fixture = createTopic("student-reader-locale-");
        String marker = shortMarker();
        Long lectureId = createAndPublishLecture(
                fixture.topicId(),
                "Русский заголовок " + marker,
                "Қазақша тақырып " + marker,
                "<p>Русский материал " + marker + "</p>",
                "<p>Қазақша материал " + marker + "</p>"
        );

        String russianReader = studentLectureHtml(lectureId, null);
        assertThat(russianReader)
                .contains("Русский заголовок " + marker)
                .contains("Русский материал " + marker)
                .doesNotContain("Қазақша тақырып " + marker)
                .doesNotContain("Қазақша материал " + marker)
                .doesNotContain("data-reader-tab=\"ru\"")
                .doesNotContain("data-reader-tab=\"kk\"");

        String kazakhReader = studentLectureHtml(lectureId, "kk");
        assertThat(kazakhReader)
                .contains("Қазақша тақырып " + marker)
                .contains("Қазақша материал " + marker)
                .doesNotContain("Русский заголовок " + marker)
                .doesNotContain("Русский материал " + marker);

        assertRussianFallbackForIncompleteKazakhVersion(fixture, marker);
        assertAdminPreviewRemainsBilingual(lectureId, marker);
    }

    /** Проверяет, что открытие лекции одним учеником не меняет статус другого. */
    @Test
    void progressIsIsolatedBetweenStudents() throws Exception {
        TopicFixture fixture = createTopic("student-isolation-");
        String marker = shortMarker();
        Long lectureId = createAndPublishLecture(fixture.topicId(), "Изолированный урок " + marker, null);
        String secondStudentEmail = "lecture-student-" + marker + "@damulab.kz";
        AppUser secondUser = users.save(new AppUser(secondStudentEmail, "not-used", "Second Student", null));
        StudentProfile secondStudent = students.save(new StudentProfile(secondUser, 4, "ru"));

        mockMvc.perform(get("/student/lectures/{id}", lectureId)
                        .with(user(STUDENT_EMAIL).roles("STUDENT")))
                .andExpect(status().isOk());

        assertThat(progressRepository.findByStudentProfileIdAndLectureId(secondStudent.getId(), lectureId))
                .isEmpty();
        mockMvc.perform(get(
                            "/student/lectures/subjects/{subjectId}/grades/{gradeId}",
                            fixture.subjectId(),
                            fixture.gradeId()
                    )
                        .with(user(secondStudentEmail).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Не начата")));
    }

    /** Проверяет блокировку завершения, безлимитный повтор и успешную сдачу теста. */
    @Test
    void checkpointLectureRequiresAllCorrectAnswersAndAllowsRetry() throws Exception {
        TopicFixture fixture = createTopic("student-checkpoint-");
        Long questionVersionId = createPublishedQuestionVersion(fixture);
        Long lectureId = createAndPublishLecture(
                fixture.topicId(),
                "Урок с тестом " + shortMarker(),
                questionVersionId
        );
        Long checkpointId = publishedLecture(lectureId).path("checkpoints").get(0).path("id").asLong();

        // Открытие создаёт IN_PROGRESS, но прямой POST завершения до сдачи блокируется.
        mockMvc.perform(get("/student/lectures/{id}", lectureId)
                        .with(user(STUDENT_EMAIL).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-lesson-tab=\"theory\"")))
                .andExpect(content().string(containsString("data-lesson-tab=\"testing\"")))
                .andExpect(content().string(containsString("data-lesson-panel=\"testing\"")))
                .andExpect(content().string(containsString("Проверить ответы")));
        mockMvc.perform(post("/student/lectures/{id}/complete", lectureId)
                        .with(user(STUDENT_EMAIL).roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(progress(lectureId).getStatus()).isEqualTo(StudentLectureStatus.IN_PROGRESS);

        // Первая попытка намеренно неверная. Прогресс остаётся открытым для повторения.
        mockMvc.perform(post("/student/lectures/{id}/checkpoints", lectureId)
                        .with(user(STUDENT_EMAIL).roles("STUDENT"))
                        .with(csrf())
                        .param("answer_" + checkpointId, "A"))
                .andExpect(status().is3xxRedirection());
        assertThat(progress(lectureId).getCheckpointAttemptCount()).isEqualTo(1);
        assertThat(progress(lectureId).getCheckpointPassedAt()).isNull();

        // Правильный вариант B сдаёт тест; отдельная кнопка после этого завершает лекцию.
        mockMvc.perform(post("/student/lectures/{id}/checkpoints", lectureId)
                        .with(user(STUDENT_EMAIL).roles("STUDENT"))
                        .with(csrf())
                        .param("answer_" + checkpointId, "B"))
                .andExpect(status().is3xxRedirection());
        assertThat(progress(lectureId).getCheckpointAttemptCount()).isEqualTo(2);
        assertThat(progress(lectureId).getCheckpointPassedAt()).isNotNull();

        mockMvc.perform(post("/student/lectures/{id}/complete", lectureId)
                        .with(user(STUDENT_EMAIL).roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(progress(lectureId).getStatus()).isEqualTo(StudentLectureStatus.DONE);
    }

    /** Проверяет единый DOM-набор полей и серверную проверку всех поддерживаемых типов. */
    @Test
    void checkpointNavigatorSupportsEveryExistingQuestionType() throws Exception {
        TopicFixture fixture = createTopic("student-all-checkpoints-");
        List<Long> versions = List.of(
                createPublishedQuestionVersion(fixture, "SCQ"),
                createPublishedQuestionVersion(fixture, "MCQ"),
                createPublishedQuestionVersion(fixture, "MATCHING"),
                createPublishedQuestionVersion(fixture, "FILL_IN")
        );
        Long lectureId = createAndPublishLectureWithCheckpoints(
                fixture.topicId(),
                "Все типы вопросов " + shortMarker(),
                versions
        );
        JsonNode checkpoints = publishedLecture(lectureId).path("checkpoints");
        assertThat(checkpoints.size()).isEqualTo(4);
        Map<String, Long> checkpointIds = new LinkedHashMap<>();
        checkpoints.forEach(node -> checkpointIds.put(node.path("type").asText(), node.path("id").asLong()));

        Long scqId = checkpointIds.get("SCQ");
        Long mcqId = checkpointIds.get("MCQ");
        Long matchingId = checkpointIds.get("MATCHING");
        Long fillId = checkpointIds.get("FILL_IN");

        String page = studentLectureHtml(lectureId, null);
        assertThat(page)
                .contains("data-question-navigator")
                .contains("data-incomplete-summary")
                .contains("data-progress-template=\"Отвечено @@answered@@ из @@total@@\"")
                .doesNotContain("Отвечено answered из total")
                .contains("Остались вопросы")
                .contains("name=\"answer_" + scqId + "\"")
                .contains("name=\"answer_" + mcqId + "\"")
                .contains("name=\"match_" + matchingId + "_0\"")
                .contains("name=\"match_" + matchingId + "_1\"")
                .contains("name=\"fill_" + fillId + "_0\"");

        // Все поля отправляются одной формой; первая заполненная попытка намеренно неверная.
        mockMvc.perform(post("/student/lectures/{id}/checkpoints", lectureId)
                        .with(user(STUDENT_EMAIL).roles("STUDENT"))
                        .with(csrf())
                        .param("answer_" + scqId, "A")
                        .param("answer_" + mcqId, "A")
                        .param("match_" + matchingId + "_0", "0.25")
                        .param("match_" + matchingId + "_1", "0.5")
                        .param("fill_" + fillId + "_0", "29"))
                .andExpect(status().is3xxRedirection());
        assertThat(progress(lectureId).getCheckpointAttemptCount()).isEqualTo(1);
        assertThat(progress(lectureId).getCheckpointPassedAt()).isNull();

        mockMvc.perform(post("/student/lectures/{id}/checkpoints", lectureId)
                        .with(user(STUDENT_EMAIL).roles("STUDENT"))
                        .with(csrf())
                        .param("answer_" + scqId, "B")
                        .param("answer_" + mcqId, "A", "C")
                        .param("match_" + matchingId + "_0", "0.5")
                        .param("match_" + matchingId + "_1", "0.25")
                        .param("fill_" + fillId + "_0", "30"))
                .andExpect(status().is3xxRedirection());
        assertThat(progress(lectureId).getCheckpointAttemptCount()).isEqualTo(2);
        assertThat(progress(lectureId).getCheckpointPassedAt()).isNotNull();
    }

    /** Загружает student reader с указанной глобальной локалью приложения. */
    private String studentLectureHtml(Long lectureId, String language) throws Exception {
        var request = get("/student/lectures/{id}", lectureId)
                .with(user(STUDENT_EMAIL).roles("STUDENT"));
        if (language != null) {
            request.param("lang", language);
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** Воспроизводит неполную старую запись, которую KK-reader должен показать по-русски. */
    private void assertRussianFallbackForIncompleteKazakhVersion(
            TopicFixture fixture,
            String marker
    ) throws Exception {
        Long lectureId = createAndPublishLecture(
                fixture.topicId(),
                "Fallback заголовок " + marker,
                "Уақытша қазақша атау " + marker,
                "<p>Fallback материал " + marker + "</p>",
                "<p>Уақытша қазақша мәтін " + marker + "</p>"
        );
        // Публикация требует обе версии; прямое изменение имитирует неполные legacy-данные.
        jdbcTemplate.update("""
                update lecture_versions
                set title_kk = null, content_kk_html = null
                where id = (select current_version_id from lectures where id = ?)
                """, lectureId);

        String fallbackReader = studentLectureHtml(lectureId, "kk");
        assertThat(fallbackReader)
                .contains("Fallback заголовок " + marker)
                .contains("Fallback материал " + marker);
    }

    /** Admin preview по-прежнему выводит обе авторские языковые версии. */
    private void assertAdminPreviewRemainsBilingual(Long lectureId, String marker) throws Exception {
        mockMvc.perform(get("/admin/lectures/{id}/preview", lectureId)
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-reader-tab=\"ru\"")))
                .andExpect(content().string(containsString("data-reader-tab=\"kk\"")))
                .andExpect(content().string(containsString("Русский материал " + marker)))
                .andExpect(content().string(containsString("Қазақша материал " + marker)));
    }

    /** Создаёт тему математики четвёртого класса через публичный административный API. */
    private TopicFixture createTopic(String prefix) throws Exception {
        Long subjectId = subjects.findAllByOrderByTitleRuAsc().stream()
                .filter(subject -> "math".equals(subject.getCode()))
                .findFirst()
                .orElseThrow()
                .getId();
        return createTopic(prefix, subjectId, 4);
    }

    /** Создаёт тему указанного предмета и класса через тот же API, которым пользуется администратор. */
    private TopicFixture createTopic(String prefix, long subjectId, int gradeNo) throws Exception {
        Long gradeId = grades.findAllByOrderByGradeNoAsc().stream()
                .filter(grade -> Integer.valueOf(gradeNo).equals(grade.getGradeNo()))
                .findFirst()
                .orElseThrow()
                .getId();
        String suffix = shortMarker();
        String response = mockMvc.perform(post("/api/admin/topics")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": %d,
                                  "gradeId": %d,
                                  "code": "%s%s",
                                  "titleRu": "Тема %s",
                                  "titleKk": "Тақырып %s"
                                }
                                """.formatted(subjectId, gradeId, prefix, suffix, suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new TopicFixture(idFrom(response), subjectId, gradeId);
    }

    /** Создаёт SCQ с правильным вариантом B и публикует его для ручного checkpoint. */
    private Long createPublishedQuestionVersion(TopicFixture fixture) throws Exception {
        return createPublishedQuestionVersion(fixture, "SCQ");
    }

    /** Создаёт и публикует вопрос одного из четырёх уже существующих типов. */
    private Long createPublishedQuestionVersion(TopicFixture fixture, String type) throws Exception {
        String payload = switch (type) {
            case "SCQ" -> """
                    {
                      "subjectId": %d,
                      "topicIds": [%d],
                      "gradeIds": [%d],
                      "type": "SCQ",
                      "difficulty": 2,
                      "bodyRu": "Сколько будет два плюс два?",
                      "bodyKk": "Екі қосу екі нешеге тең?",
                      "source": "student-lecture-test",
                      "options": [
                        {"label":"A","textRu":"3","textKk":"3","correct":false},
                        {"label":"B","textRu":"4","textKk":"4","correct":true}
                      ]
                    }
                    """.formatted(fixture.subjectId(), fixture.topicId(), fixture.gradeId());
            case "MCQ" -> """
                    {
                      "subjectId": %d,
                      "topicIds": [%d],
                      "gradeIds": [%d],
                      "type": "MCQ",
                      "difficulty": 2,
                      "bodyRu": "Выберите чётные числа",
                      "bodyKk": "Жұп сандарды таңдаңыз",
                      "source": "student-lecture-test",
                      "options": [
                        {"label":"A","textRu":"2","textKk":"2","correct":true},
                        {"label":"B","textRu":"3","textKk":"3","correct":false},
                        {"label":"C","textRu":"4","textKk":"4","correct":true}
                      ]
                    }
                    """.formatted(fixture.subjectId(), fixture.topicId(), fixture.gradeId());
            case "MATCHING" -> """
                    {
                      "subjectId": %d,
                      "topicIds": [%d],
                      "gradeIds": [%d],
                      "type": "MATCHING",
                      "difficulty": 3,
                      "bodyRu": "Соотнесите проценты и дроби",
                      "bodyKk": "Пайыздар мен бөлшектерді сәйкестендіріңіз",
                      "source": "student-lecture-test",
                      "matchingPairs": [
                        {"leftRu":"50%%","leftKk":"50%%","rightRu":"0.5","rightKk":"0.5"},
                        {"leftRu":"25%%","leftKk":"25%%","rightRu":"0.25","rightKk":"0.25"}
                      ]
                    }
                    """.formatted(fixture.subjectId(), fixture.topicId(), fixture.gradeId());
            case "FILL_IN" -> """
                    {
                      "subjectId": %d,
                      "topicIds": [%d],
                      "gradeIds": [%d],
                      "type": "FILL_IN",
                      "difficulty": 2,
                      "bodyRu": "15%% от 200 равно [[1]]",
                      "bodyKk": "200 санының 15 пайызы [[1]]",
                      "source": "student-lecture-test",
                      "fillAnswers": [
                        {"placeholder":"[[1]]","answer":"30","matchMode":"NUMERIC_TOLERANCE","tolerance":0.01}
                      ]
                    }
                    """.formatted(fixture.subjectId(), fixture.topicId(), fixture.gradeId());
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
        String response = mockMvc.perform(post("/api/admin/questions")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode question = objectMapper.readTree(response);
        Long questionId = question.path("id").asLong();
        Long versionId = question.path("currentVersionId").asLong();

        mockMvc.perform(post("/api/admin/questions/{id}/approve", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/questions/{id}/publish", questionId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
        return versionId;
    }

    /** Создаёт черновик лекции без теста либо с одним ручным checkpoint. */
    private Long createLecture(Long topicId, String title, Long questionVersionId) throws Exception {
        return createLectureWithCheckpoints(
                topicId,
                title,
                questionVersionId == null ? List.of() : List.of(questionVersionId)
        );
    }

    /** Создаёт черновик лекции со всеми указанными ручными checkpoint. */
    private Long createLectureWithCheckpoints(Long topicId, String title, List<Long> questionVersionIds) throws Exception {
        String controlMode = questionVersionIds.isEmpty() ? "NONE" : "MANUAL";
        String checkpointJson = questionVersionIds.isEmpty()
                ? ""
                : ", \"checkpointQuestionVersionIds\": ["
                        + questionVersionIds.stream().map(String::valueOf).collect(Collectors.joining(","))
                        + "]";
        String response = mockMvc.perform(post("/api/admin/lectures")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "titleRu": "%s",
                                  "titleKk": "%s",
                                  "contentRu": "Материал урока",
                                  "contentKk": "Сабақ материалы",
                                  "source": "student-lecture-integration",
                                  "controlMode": "%s"%s
                                }
                                """.formatted(topicId, title, title, controlMode, checkpointJson)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return idFrom(response);
    }

    /** Создаёт и публикует двуязычную лекцию с различимыми версиями для locale-тестов. */
    private Long createAndPublishLecture(
            Long topicId,
            String titleRu,
            String titleKk,
            String contentRu,
            String contentKk
    ) throws Exception {
        String response = mockMvc.perform(post("/api/admin/lectures")
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": %d,
                                  "titleRu": "%s",
                                  "titleKk": "%s",
                                  "contentRu": "%s",
                                  "contentKk": "%s",
                                  "source": "student-lecture-locale-integration",
                                  "controlMode": "NONE"
                                }
                                """.formatted(topicId, titleRu, titleKk, contentRu, contentKk)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long lectureId = idFrom(response);
        mockMvc.perform(post("/api/admin/lectures/{id}/publish", lectureId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
        return lectureId;
    }

    /** Создаёт лекцию и отдельно публикует её, чтобы она стала видна ученикам. */
    private Long createAndPublishLecture(Long topicId, String title, Long questionVersionId) throws Exception {
        Long lectureId = createLecture(topicId, title, questionVersionId);
        mockMvc.perform(post("/api/admin/lectures/{id}/publish", lectureId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
        return lectureId;
    }

    /** Создаёт и публикует лекцию с несколькими ручными checkpoint. */
    private Long createAndPublishLectureWithCheckpoints(
            Long topicId,
            String title,
            List<Long> questionVersionIds
    ) throws Exception {
        Long lectureId = createLectureWithCheckpoints(topicId, title, questionVersionIds);
        mockMvc.perform(post("/api/admin/lectures/{id}/publish", lectureId)
                        .with(user("admin@damulab.kz").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
        return lectureId;
    }

    /** Загружает опубликованную лекцию через admin API для получения checkpoint id. */
    private JsonNode publishedLecture(Long lectureId) throws Exception {
        String response = mockMvc.perform(get("/api/admin/lectures/{id}", lectureId)
                        .with(user("admin@damulab.kz").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    /** Возвращает прогресс стандартного тестового ученика по указанной лекции. */
    private StudentLectureProgress progress(Long lectureId) {
        Long studentId = students.findByUserEmailIgnoreCase(STUDENT_EMAIL).orElseThrow().getId();
        return progressRepository.findByStudentProfileIdAndLectureId(studentId, lectureId).orElseThrow();
    }

    /** Извлекает id из короткого JSON-ответа создания сущности. */
    private Long idFrom(String response) throws Exception {
        return objectMapper.readTree(response).path("id").asLong();
    }

    /** Создаёт короткий уникальный суффикс, чтобы тесты не пересекались данными. */
    private String shortMarker() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
