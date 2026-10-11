package kz.damulab;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import kz.damulab.auth.*;
import kz.damulab.content.*;
import kz.damulab.questions.*;
import kz.damulab.quiz.*;
import kz.damulab.quiz.engine.*;
import kz.damulab.users.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Предметная игра ученика: настоящая форма, общий lifecycle, снимки и рейтинг. */
@SpringBootTest(properties="damulab.quiz.timeout-worker-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(QuizArenaIntegrationTest.TimeConfig.class)
class QuizArenaIntegrationTest {
    private static final String HOST="student@damulab.kz";
    @Autowired MockMvc mvc;
    @Autowired QuizRoomService rooms;
    @Autowired QuizRoomRepository roomRepository;
    @Autowired SubjectRepository subjects;
    @Autowired GradeRepository grades;
    @Autowired QuestionRepository questions;
    @Autowired QuestionVersionRepository versions;
    @Autowired AppUserRepository users;
    @Autowired RegistrationService registration;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired TestClock clock;
    private Long subjectId, gradeId, nextGradeId;
    private String guest, outsider;

    // Каждый прогон использует новую схему; рабочая БД и другие тестовые банки не затрагиваются.
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry registry) {
        String schema="quiz_custom_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv().getOrDefault("QUIZ_TEST_DB_URL","jdbc:h2:mem:"+schema+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        // Схема должна существовать до первого Hikari connection.setSchema().
        org.flywaydb.core.Flyway.configure().dataSource(url,System.getenv().getOrDefault("QUIZ_TEST_DB_USER","sa"),System.getenv().getOrDefault("QUIZ_TEST_DB_PASSWORD",""))
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate();
        registry.add("spring.datasource.url",()->url);
        registry.add("spring.datasource.hikari.schema",()->schema);
        registry.add("spring.flyway.schemas",()->schema);
        registry.add("spring.flyway.default-schema",()->schema);
    }

    @BeforeEach void prepare() {
        clock.offset=0; guest=student(); outsider=student();
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            var subject=subjects.save(new Subject("custom-"+UUID.randomUUID(),"Учебный предмет","Оқу пәні","Описание","Сипаттама"));
            subjectId=subject.getId();
            var grade=grades.findAllByOrderByGradeNoAsc().stream().filter(g->g.getGradeNo()==5).findFirst().orElseThrow();
            var next=grades.findAllByOrderByGradeNoAsc().stream().filter(g->g.getGradeNo()==4).findFirst().orElseThrow();
            gradeId=grade.getId(); nextGradeId=next.getId();
            for(int i=0;i<16;i++) {
                var q=questions.save(new Question(QuestionStatus.PUBLISHED,users.findByEmailIgnoreCase(HOST).orElseThrow()));
                var v=new QuestionVersion(q,1,QuestionType.SCQ,subject,null,1,"Вопрос "+i,"Сұрақ "+i,"Разбор","Түсіндірме",null,null,"Учебник",
                    "[{\"label\":\"A\",\"textRu\":\"Первый\",\"textKk\":\"Бірінші\"},{\"label\":\"B\",\"textRu\":\"Второй\",\"textKk\":\"Екінші\"}]","[\"B\"]");
                v.markPublished(); v.getGradeLinks().add(new QuestionVersionGrade(v,grade)); v.getGradeLinks().add(new QuestionVersionGrade(v,next));
                versions.saveAndFlush(v); q.setCurrentVersion(v);
            }
        });
    }

    @Test void formDefaultsComeFromCurrentSiteLanguageNotProfile() throws Exception {
        for(String locale:List.of("ru","kk")) {
            String html=mvc.perform(get("/student/quiz/create").param("lang",locale).with(user(HOST).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(view().name("student/quiz-create")).andReturn().getResponse().getContentAsString();
            var doc=org.jsoup.Jsoup.parse(html);
            assertThat(doc.select("select[name=language] option[selected]").val()).isEqualTo(locale);
            assertThat(doc.select("input[name=questionCount]").val()).isEqualTo("15");
            assertThat(doc.select("input[name=roundSeconds]").val()).isEqualTo("5");
            assertThat(doc.select("[name=maxPlayers]")).isEmpty(); assertThat(doc.select("[name=difficulty]")).isEmpty();
        }
        mvc.perform(get("/student/quiz").with(user(HOST).roles("STUDENT"))).andExpect(view().name("student/quiz-games"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/student/quiz/create")));
    }
    @Test void defaultsRetryAndStartAreIdempotent() {
        String key=UUID.randomUUID().toString();
        var request=new QuizGameRoomRequest.CustomCreate(subjectId,gradeId,"kk",null,null,key);
        var room=rooms.createCustom(HOST,request);
        assertThat(room.questionCount()).isEqualTo(15); assertThat(room.roundSeconds()).isEqualTo(5);
        assertThat(room.maxPlayers()).isEqualTo(4); assertThat(room.language()).isEqualTo("kk"); assertThat(room.gameCode()).isNull();
        assertThat(rooms.createCustom(HOST,request).code()).isEqualTo(room.code()); assertThat(roundCount(room)).isZero();
        rooms.start(room.code(),HOST); assertThat(roundCount(room)).isEqualTo(15);
        rooms.start(room.code(),HOST); assertThat(roundCount(room)).isEqualTo(15);
        assertThatThrownBy(()->rooms.createCustom(HOST,new QuizGameRoomRequest.CustomCreate(subjectId,gradeId,"ru",15,10,key))).hasMessage("IDEMPOTENCY_CONFLICT");
    }
    @Test void formCreatesRoomAndRetainsInputsOnError() throws Exception {
        String redirect=mvc.perform(post("/student/quiz/rooms").with(user(HOST).roles("STUDENT")).with(csrf())
            .param("subjectId",subjectId.toString()).param("gradeId",gradeId.toString()).param("language","kk").param("idempotencyKey",UUID.randomUUID().toString()))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        mvc.perform(get(redirect).with(user(HOST).roles("STUDENT"))).andExpect(status().isOk()).andExpect(view().name("student/quiz-game-room"));
        mvc.perform(post("/student/quiz/rooms").with(user(HOST).roles("STUDENT")).with(csrf())
            .param("subjectId",subjectId.toString()).param("gradeId",gradeId.toString()).param("language","kk")
            .param("questionCount","17").param("roundSeconds","10").param("idempotencyKey",UUID.randomUUID().toString()))
            .andExpect(status().isOk()).andExpect(view().name("student/quiz-create")).andExpect(model().attributeExists("error"))
            .andExpect(model().attribute("studentQuizForm",org.hamcrest.Matchers.hasProperty("language",org.hamcrest.Matchers.is("kk"))));
    }
    @Test void fourPlayersAllowedRegardlessOfProfileClassAndFifthRejected() {
        var room=create(2); rooms.join(room.code(),guest); rooms.join(room.code(),student()); rooms.join(room.code(),student());
        assertThat(rooms.state(room.code(),HOST).participants()).hasSize(4);
        assertThatThrownBy(()->rooms.join(room.code(),outsider)).hasMessage("ROOM_FULL");
    }
    @Test void changingClassAndTimingResetsGuestReadiness() {
        var room=create(2); rooms.join(room.code(),guest); rooms.ready(room.code(),guest,new QuizGameRoomRequest.Ready(true,0));
        var changed=rooms.settings(room.code(),HOST,new QuizGameRoomRequest.Settings(nextGradeId,2,15,0));
        assertThat(changed.gradeId()).isEqualTo(nextGradeId);
        assertThat(jdbc.queryForObject("select grade_id from quiz_rooms where id=?",Long.class,room.id())).isEqualTo(nextGradeId);
        assertThat(changed.participants().stream().filter(p->!p.host()).findFirst().orElseThrow().ready()).isFalse();
        assertThatThrownBy(()->rooms.start(room.code(),HOST)).hasMessage("PARTICIPANT_NOT_READY");
        assertThatThrownBy(()->rooms.ready(room.code(),guest,new QuizGameRoomRequest.Ready(true,0))).hasMessage("SETTINGS_REVISION_CONFLICT");
        rooms.ready(room.code(),guest,new QuizGameRoomRequest.Ready(true,changed.settingsRevision()));
        assertThat(rooms.start(room.code(),HOST).status()).isEqualTo("active");
    }
    @Test void twoStudentsPlayWithSafeCurrentRoundAndOwnResults() throws Exception {
        var room=start(2);
        for(int i=0;i<2;i++) {
            clock.offset=i*10+1; var current=rooms.state(room.code(),HOST).currentRound();
            assertThat(mapper.writeValueAsString(current)).doesNotContain("answerKey","correctAnswer","sourceReference","explanation");
            var answer=new QuizGameRoomRequest.Answer(current.id(),mapper.readTree("{\"selected\":[\"B\"]}"),false);
            rooms.answer(room.code(),HOST,answer); rooms.answer(room.code(),guest,answer);
            assertThat(rooms.state(room.code(),HOST).currentRound().answered()).isTrue();
        }
        clock.offset=20; new QuizTimeoutScheduler(rooms).enforceTimeouts();
        var result=rooms.results(room.code(),HOST);
        assertThat(result.me().percent()).isEqualTo(100); assertThat(result.me().totalResponseMs()).isEqualTo(2000);
        assertThat(result.leaderboard()).hasSize(2); assertThat(result.review()).hasSize(2);
        assertThatThrownBy(()->rooms.results(room.code(),outsider)).hasMessage("PARTICIPANT_REQUIRED");
        mvc.perform(get("/student/quiz/rooms/"+room.code()+"/results").with(user(HOST).roles("STUDENT")))
            .andExpect(status().isOk()).andExpect(view().name("student/quiz-game-room"));
    }
    @Test void unansweredRoundsTimeoutAndLateAnswerIsRejected() throws Exception {
        var room=start(1); var current=room.currentRound(); clock.offset=10;
        assertThatThrownBy(()->rooms.answer(room.code(),HOST,new QuizGameRoomRequest.Answer(current.id(),mapper.readTree("{\"selected\":[\"B\"]}"),false)))
            .isInstanceOf(QuizEngineException.class);
        rooms.tick(room.code()); var result=rooms.results(room.code(),HOST);
        assertThat(result.me().percent()).isZero(); assertThat(result.review().getFirst().timedOut()).isTrue();
    }
    @Test void invalidSettingsAndInsufficientBankDoNotCreateRooms() {
        long before=roomRepository.count();
        assertThatThrownBy(()->create(17)).hasMessage("INSUFFICIENT_PUBLISHED_CONTENT");
        for(int seconds:List.of(0,121)) assertThatThrownBy(()->rooms.createCustom(HOST,new QuizGameRoomRequest.CustomCreate(subjectId,gradeId,"ru",1,seconds,UUID.randomUUID().toString()))).hasMessage("SETTINGS_INVALID");
        assertThatThrownBy(()->rooms.createCustom(HOST,new QuizGameRoomRequest.CustomCreate(subjectId,gradeId,"en",1,10,UUID.randomUUID().toString()))).hasMessage("REQUEST_INVALID");
        assertThat(roomRepository.count()).isEqualTo(before);
    }
    @Test void apiRejectsPlayerCountAndProtectsMembershipAndCsrf() throws Exception {
        String json=mapper.writeValueAsString(Map.of("subjectId",subjectId,"gradeId",gradeId,"language","ru","questionCount",1,"idempotencyKey",UUID.randomUUID().toString()));
        mvc.perform(post("/api/quiz/rooms").with(user(HOST).roles("STUDENT")).contentType("application/json").content(json)).andExpect(status().isForbidden());
        mvc.perform(post("/api/quiz/rooms").with(user(HOST).roles("STUDENT")).with(csrf()).contentType("application/json").content(json.substring(0,json.length()-1)+",\"maxPlayers\":8}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("REQUEST_INVALID"));
        var room=create(1);
        mvc.perform(get("/api/quiz/rooms/"+room.code()).with(user(outsider).roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/quiz/rooms/"+room.code()).with(user(HOST).roles("PARENT"))).andExpect(status().isForbidden());
    }
    private QuizGameRoomResponse create(int count) {return rooms.createCustom(HOST,new QuizGameRoomRequest.CustomCreate(subjectId,gradeId,"ru",count,10,UUID.randomUUID().toString()));}
    private QuizGameRoomResponse start(int count) {var r=create(count); rooms.join(r.code(),guest); rooms.ready(r.code(),guest,new QuizGameRoomRequest.Ready(true,0)); return rooms.start(r.code(),HOST);}
    private int roundCount(QuizGameRoomResponse r) {return jdbc.queryForObject("select count(*) from quiz_rounds where room_id=?",Integer.class,r.id());}
    private String student() {
        var f=new RegisterForm(); String email=UUID.randomUUID()+"@custom.test"; f.setEmail(email); f.setPassword("password123"); f.setFullName("Ученик");
        f.setRole(RoleCode.STUDENT); f.setGradeNo(4); f.setPreferredLanguage("ru"); registration.register(f); return email;
    }
    /** Серверное время управляется тестом: ожидать реальные раунды не требуется. */
    static class TestClock extends Clock {
        long offset; public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId z){return this;}
        public Instant instant(){return Instant.parse("2026-10-10T12:00:00Z").plusSeconds(offset);}
    }
    @TestConfiguration static class TimeConfig {@Bean @Primary TestClock quizClock(){return new TestClock();}}
}
