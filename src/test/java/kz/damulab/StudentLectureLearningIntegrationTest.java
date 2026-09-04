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

import kz.damulab.content.GradeRepository;
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

    /** Проверяет двухуровневый каталог, сортировку и начальный статус лекции. */
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

        String subjectPage = mockMvc.perform(get("/student/lectures/subjects/{id}", fixture.subjectId())
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

    /** Проверяет простой путь NOT_STARTED → IN_PROGRESS → DONE без теста. */
    @Test
    void lectureWithoutCheckpointsCanBeCompletedImmediately() throws Exception {
        TopicFixture fixture = createTopic("student-simple-progress-");
        Long lectureId = createAndPublishLecture(fixture.topicId(), "Урок без теста " + shortMarker(), null);

        mockMvc.perform(get("/student/lectures/{id}", lectureId)
                        .with(user(STUDENT_EMAIL).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("student/lecture"))
                .andExpect(content().string(containsString("Завершить изучение")));

        assertThat(progress(lectureId).getStatus()).isEqualTo(StudentLectureStatus.IN_PROGRESS);

        mockMvc.perform(post("/student/lectures/{id}/complete", lectureId)
                        .with(user(STUDENT_EMAIL).roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/lectures/" + lectureId));

        assertThat(progress(lectureId).getStatus()).isEqualTo(StudentLectureStatus.DONE);
        assertThat(progress(lectureId).getCompletedAt()).isNotNull();
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
        mockMvc.perform(get("/student/lectures/subjects/{id}", fixture.subjectId())
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

    /** Создаёт тему математики четвёртого класса через публичный административный API. */
    private TopicFixture createTopic(String prefix) throws Exception {
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
        String response = mockMvc.perform(post("/api/admin/questions")
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
                                  "bodyRu": "Сколько будет два плюс два?",
                                  "bodyKk": "Екі қосу екі нешеге тең?",
                                  "source": "student-lecture-test",
                                  "options": [
                                    {"label":"A","textRu":"3","textKk":"3","correct":false},
                                    {"label":"B","textRu":"4","textKk":"4","correct":true}
                                  ]
                                }
                                """.formatted(fixture.subjectId(), fixture.topicId(), fixture.gradeId())))
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

    /** Создаёт и публикует лекцию без теста либо с одним ручным checkpoint. */
    private Long createAndPublishLecture(Long topicId, String title, Long questionVersionId) throws Exception {
        String controlMode = questionVersionId == null ? "NONE" : "MANUAL";
        String checkpointJson = questionVersionId == null
                ? ""
                : ", \"checkpointQuestionVersionIds\": [" + questionVersionId + "]";
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
        Long lectureId = idFrom(response);
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
