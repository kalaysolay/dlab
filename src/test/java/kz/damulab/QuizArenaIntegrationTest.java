package kz.damulab;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import kz.damulab.auth.RegisterForm;
import kz.damulab.auth.RegistrationService;
import kz.damulab.content.GradeRepository;
import kz.damulab.content.SubjectRepository;
import kz.damulab.users.AppUserRepository;
import kz.damulab.users.RoleCode;

@SpringBootTest(properties = "damulab.quiz.timeout-worker-delay-ms=600000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuizArenaIntegrationTest {

    private static final String HOST = "student@damulab.kz";
    private static final String GUEST = "quiz-guest@damulab.kz";
    private static final String OUTSIDER = "quiz-outsider@damulab.kz";
    private static final Instant BASE_TIME = Instant.parse("2026-04-28T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubjectRepository subjects;

    @Autowired
    private GradeRepository grades;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MutableTestClock clock;

    @BeforeEach
    void setUp() {
        clock.set(BASE_TIME);
        createStudent(GUEST, "Quiz Guest");
        createStudent(OUTSIDER, "Quiz Outsider");
    }

    private void createStudent(String email, String fullName) {
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }
        RegisterForm form = new RegisterForm();
        form.setEmail(email);
        form.setPassword("password123");
        form.setFullName(fullName);
        form.setRole(RoleCode.STUDENT);
        form.setGradeNo(4);
        form.setPreferredLanguage("ru");
        registrationService.register(form);
    }

    @Test
    void twoStudentsCanPlayQuizEndToEndWithoutReceivingAnswerKeyBeforeResults() throws Exception {
        JsonNode active = startTwoStudentRoom(4, 5);
        String code = active.path("code").asText();

        org.assertj.core.api.Assertions.assertThat(active.path("rounds").size()).isGreaterThanOrEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(active.toString()).doesNotContain("answerKey").doesNotContain("correctAnswer");

        for (int i = 0; i < active.path("rounds").size(); i++) {
            JsonNode round = active.path("rounds").get(i);
            clock.set(BASE_TIME.plusSeconds((long) i * 5));
            submitAnswer(code, HOST, round).andExpect(status().isOk());

            if (i == 0) {
                mockMvc.perform(get("/api/quiz/rooms/{code}/results", code)
                                .with(user(HOST).roles("STUDENT")))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().string(containsString("room_not_finished")));
            }

            submitAnswer(code, GUEST, round).andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/quiz/rooms/{code}/results", code)
                        .with(user(HOST).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("finished"))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].percent").value(100));
    }

    @Test
    void futureRoundAndLateAnswersAreRejectedByServerTime() throws Exception {
        JsonNode active = startTwoStudentRoom(2, 5);
        String code = active.path("code").asText();
        JsonNode firstRound = active.path("rounds").get(0);
        JsonNode secondRound = active.path("rounds").get(1);

        submitAnswer(code, HOST, secondRound)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("round_not_open")));

        clock.set(BASE_TIME.plusSeconds(8));

        submitAnswer(code, HOST, firstRound)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("late_answer")));
    }

    @Test
    void timeoutAutoSubmitAcceptsAnswerShortlyAfterRoundEnds() throws Exception {
        JsonNode active = startTwoStudentRoom(1, 5);
        String code = active.path("code").asText();
        JsonNode firstRound = active.path("rounds").get(0);

        clock.set(BASE_TIME.plusSeconds(6));

        submitAnswer(code, HOST, firstRound)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rounds[0].answered").value(true));

        submitAnswer(code, GUEST, firstRound).andExpect(status().isOk());

        mockMvc.perform(get("/api/quiz/rooms/{code}/results", code)
                        .with(user(HOST).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("finished"))
                .andExpect(jsonPath("$.results[0].percent").value(100));
    }

    @Test
    void timeoutCreatesZeroAnswersAndFinishesRoomWhenAllRoundsExpire() throws Exception {
        JsonNode active = startTwoStudentRoom(1, 5);
        String code = active.path("code").asText();
        JsonNode firstRound = active.path("rounds").get(0);

        clock.set(BASE_TIME.plusSeconds(8));

        submitAnswer(code, HOST, firstRound)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("late_answer")));

        mockMvc.perform(get("/api/quiz/rooms/{code}/results", code)
                        .with(user(HOST).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("finished"))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].percent").value(0));
    }

    @Test
    void studentQuizPagesAreServerRendered() throws Exception {
        mockMvc.perform(get("/student/quiz")
                        .with(user(HOST).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("student/quiz-hub"))
                .andExpect(content().string(containsString("Quiz Arena")));

        String location = mockMvc.perform(post("/student/quiz/rooms")
                        .with(user(HOST).roles("STUDENT"))
                        .with(csrf())
                        .param("subjectId", subjectId().toString())
                        .param("gradeId", gradeId().toString())
                        .param("language", "ru")
                        .param("questionCount", "4")
                        .param("roundSeconds", "20")
                        .param("maxPlayers", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/student/quiz/rooms/*"))
                .andReturn()
                .getResponse()
                .getRedirectedUrl();

        mockMvc.perform(get(location)
                        .with(user(HOST).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("student/quiz-room"))
                .andExpect(content().string(containsString("Комната")))
                .andExpect(content().string(not(containsString("answer_key_json"))));
    }

    @Test
    void parentCannotOpenQuizApi() throws Exception {
        mockMvc.perform(get("/api/quiz/rooms/QZ1234")
                        .with(user("parent@damulab.kz").roles("PARENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonParticipantStudentCannotReadRoomOrSubmitAnswers() throws Exception {
        JsonNode active = startTwoStudentRoom(1, 5);
        String code = active.path("code").asText();
        JsonNode firstRound = active.path("rounds").get(0);

        mockMvc.perform(get("/api/quiz/rooms/{code}", code)
                        .with(user(OUTSIDER).roles("STUDENT")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("participant_not_found")));

        submitAnswer(code, OUTSIDER, firstRound)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("participant_not_found")));
    }

    private JsonNode createRoom() throws Exception {
        String response = mockMvc.perform(post("/api/quiz/rooms")
                        .with(user(HOST).roles("STUDENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": %d,
                                  "gradeId": %d,
                                  "language": "ru",
                                  "questionCount": 4,
                                  "roundSeconds": 5,
                                  "maxPlayers": 4
                                }
                                """.formatted(subjectId(), gradeId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("waiting"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode startTwoStudentRoom(int questionCount, int roundSeconds) throws Exception {
        String response = mockMvc.perform(post("/api/quiz/rooms")
                        .with(user(HOST).roles("STUDENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": %d,
                                  "gradeId": %d,
                                  "language": "ru",
                                  "questionCount": %d,
                                  "roundSeconds": %d,
                                  "maxPlayers": 4
                                }
                                """.formatted(subjectId(), gradeId(), questionCount, roundSeconds)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode room = objectMapper.readTree(response);
        String code = room.path("code").asText();

        mockMvc.perform(post("/api/quiz/rooms/{code}/join", code)
                        .with(user(GUEST).roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(2));

        mockMvc.perform(post("/api/quiz/rooms/{code}/ready", code)
                        .with(user(HOST).roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/quiz/rooms/{code}/ready", code)
                        .with(user(GUEST).roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isOk());

        String activeResponse = mockMvc.perform(post("/api/quiz/rooms/{code}/start", code)
                        .with(user(HOST).roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.activeRoundId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(activeResponse);
    }

    private org.springframework.test.web.servlet.ResultActions submitAnswer(String code, String email, JsonNode round) throws Exception {
        return mockMvc.perform(post("/api/quiz/rooms/{code}/answers", code)
                .with(user(email).roles("STUDENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerBody(round)));
    }

    private String answerBody(JsonNode round) {
        long roundId = round.path("id").asLong();
        String type = round.path("type").asText();
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
                  "roundId": %d,
                  "answer": %s
                }
                """.formatted(roundId, answer);
    }

    private Long subjectId() {
        return subjects.findAllByOrderByTitleRuAsc().stream()
                .filter(subject -> "math".equals(subject.getCode()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private Long gradeId() {
        return grades.findAllByOrderByGradeNoAsc().stream()
                .filter(grade -> Integer.valueOf(4).equals(grade.getGradeNo()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @TestConfiguration
    static class QuizTestClockConfiguration {

        @Bean
        @Primary
        MutableTestClock mutableTestClock() {
            return new MutableTestClock(BASE_TIME, ZoneOffset.UTC);
        }
    }

    static class MutableTestClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        MutableTestClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableTestClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
