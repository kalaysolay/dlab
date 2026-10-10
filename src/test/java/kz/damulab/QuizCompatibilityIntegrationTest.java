package kz.damulab;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.damulab.config.DamulabQuizProperties;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.*;
import kz.damulab.quiz.engine.*;
import kz.damulab.users.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Проверяет настоящий стартовый ресурс и оба режима на мигрированной V46-истории в собственной схеме. */
@SpringBootTest(properties={"damulab.quiz.strict-self-review=true","damulab.quiz.timeout-worker-enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(QuizCompatibilityIntegrationTest.TimeConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuizCompatibilityIntegrationTest {
    @Autowired QuizStarterContentService starter;
    @Autowired QuizRoomService rooms;
    @Autowired QuizService legacy;
    @Autowired QuizStudentCatalog catalog;
    @Autowired QuizGameRepository games;
    @Autowired QuizGameAdminService adminGames;
    @Autowired AppUserRepository users;
    @Autowired StudentProfileRepository students;
    @Autowired RoleRepository roles;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired DamulabQuizProperties properties;
    @Autowired PlatformTransactionManager manager;
    @Autowired TestClock clock;
    private String reviewer;

    @DynamicPropertySource
    static void isolated(DynamicPropertyRegistry registry) throws Exception {
        String schema="quiz_compat_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv().getOrDefault("QUIZ_TEST_DB_URL","jdbc:h2:mem:"+schema+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        String user=System.getenv().getOrDefault("QUIZ_TEST_DB_USER","sa"), password=System.getenv().getOrDefault("QUIZ_TEST_DB_PASSWORD","");
        Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("46").load().migrate();
        try (var c=DriverManager.getConnection(url,user,password)) { c.setSchema(schema); QuizFoundationMigrationTest.seedLegacy(c); }
        registry.add("spring.datasource.url",()->url); registry.add("spring.datasource.username",()->user); registry.add("spring.datasource.password",()->password);
        registry.add("spring.datasource.hikari.schema",()->schema); registry.add("spring.flyway.schemas",()->schema); registry.add("spring.flyway.default-schema",()->schema);
    }
    @BeforeAll void installReviewedResource() {
        reviewer="reviewer-"+UUID.randomUUID()+"@compat.test";
        new TransactionTemplate(manager).execute(s->{var u=new AppUser(reviewer,"test-only","Проверяющий",null); u.getRoles().add(roles.findByCode(RoleCode.ADMIN).orElseThrow()); users.save(u); return null;});
        var auth=new UsernamePasswordAuthenticationToken("admin@damulab.kz",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try { assertThat(starter.install(reviewer)).containsExactlyElementsOf(QuizStarterContentService.CODES); }
        finally { SecurityContextHolder.clearContext(); }
    }
    @BeforeEach void reset() { properties.setGameCatalogEnabled(false); clock.offset=0; }

    @ParameterizedTest
    @CsvSource({"starter-en-numbers-v1,ru,1","starter-kk-numbers-v1,kk,1","starter-math-sums-v1,ru,1","starter-space-facts-v1,kk,1",
            "starter-en-numbers-v1,kk,4","starter-kk-numbers-v1,ru,4","starter-math-sums-v1,kk,4","starter-space-facts-v1,ru,4"})
    void reviewedGamesFinishInSoloAndFourPlayerRooms(String code,String locale,int count) throws Exception {
        String host=profile(locale,5); var room=rooms.create(host,new QuizGameRoomRequest.Create(code,null,null,null,UUID.randomUUID().toString()));
        List<String> players=new ArrayList<>(List.of(host));
        for(int i=1;i<count;i++) { String guest=profile(i%2==0?"ru":"kk",5); rooms.join(room.code(),guest); rooms.ready(room.code(),guest,new QuizGameRoomRequest.Ready(true,room.settingsRevision())); players.add(guest); }
        rooms.start(room.code(),host);
        for(int round=0;round<5;round++) {
            clock.offset=round*20;
            var current=rooms.state(room.code(),host).currentRound();
            String key=jdbc.queryForObject("select answer_key_snapshot_json from quiz_rounds where id=?",String.class,current.id());
            var answer=mapper.readTree(key);
            if(!answer.isBoolean()) answer=mapper.createObjectNode().set("selected",answer.isTextual()?mapper.createArrayNode().add(answer.textValue()):answer);
            for(String player:players) rooms.answer(room.code(),player,new QuizGameRoomRequest.Answer(current.id(),answer,false));
        }
        clock.offset=101;
        // Исполняется тот же метод, который вызывает scheduler, при false и без HTTP-перехода результата.
        new QuizTimeoutScheduler(legacy,rooms).enforceTimeouts();
        assertThat(jdbc.queryForObject("select status from quiz_rooms where code=?",String.class,room.code())).isEqualTo("FINISHED");
        for(String player:players) {
            var result=rooms.results(room.code(),player); assertThat(result.me().percent()).isEqualTo(100);
            assertThat(result.leaderboard()).hasSize(count); assertThat(result.review()).hasSize(5);
            assertThat(result.review()).allSatisfy(v->{ assertThat(v.sourceReference()).startsWith("https://"); assertThat(v.explanation()).isNotBlank(); });
            mvc.perform(get("/student/quiz/rooms/"+room.code()+"/results").param("lang",locale).with(user(player).roles("STUDENT")))
                    .andExpect(status().isOk()).andExpect(view().name("student/quiz-game-room"));
        }
        assertThat(properties.isGameCatalogEnabled()).isFalse();
    }
    @Test void flagSwitchRetainsCommandsResultsExpiryAndLegacyCreate() throws Exception {
        String host=profile("ru",5), guest=profile("kk",5); properties.setGameCatalogEnabled(true);
        mvc.perform(get("/student/quiz").with(user(host).roles("STUDENT"))).andExpect(view().name("student/quiz-games"));
        String json=mvc.perform(post("/api/quiz/rooms").with(user(host).roles("STUDENT")).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("gameCode",QuizStarterContentService.CODES.get(3),"idempotencyKey",UUID.randomUUID().toString())))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String code=mapper.readTree(json).path("code").asText(); properties.setGameCatalogEnabled(false);
        mvc.perform(get("/student/quiz").with(user(host).roles("STUDENT"))).andExpect(view().name("student/quiz-hub"));
        mvc.perform(get("/student/quiz/games").with(user(host).roles("STUDENT"))).andExpect(status().isNotFound());
        mvc.perform(post("/student/quiz/games/rooms").with(user(host).roles("STUDENT")).with(csrf()).param("gameCode",QuizStarterContentService.CODES.get(3)).param("idempotencyKey",UUID.randomUUID().toString())).andExpect(status().isNotFound());
        mvc.perform(get("/api/quiz/rooms/"+code+"/preview").with(user(guest).roles("STUDENT"))).andExpect(status().isOk());
        mvc.perform(post("/api/quiz/rooms/"+code+"/join").with(user(guest).roles("STUDENT")).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/quiz/rooms/"+code+"/ready").with(user(guest).roles("STUDENT")).with(csrf()).contentType("application/json").content("{\"ready\":true,\"settingsRevision\":0}")).andExpect(status().isOk());
        mvc.perform(post("/api/quiz/rooms/"+code+"/start").with(user(host).roles("STUDENT")).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/student/quiz/rooms/"+code).with(user(host).roles("STUDENT"))).andExpect(status().isOk());
        var active=rooms.state(code,host);
        mvc.perform(post("/api/quiz/rooms/"+code+"/answers").with(user(host).roles("STUDENT")).with(csrf()).contentType("application/json").content("{\"roundId\":"+active.activeRoundId()+",\"answer\":false}")).andExpect(status().isOk());
        var expired=rooms.create(host,new QuizGameRoomRequest.Create(QuizStarterContentService.CODES.get(3),null,null,null,UUID.randomUUID().toString()));
        clock.offset=1801; new QuizTimeoutScheduler(legacy,rooms).enforceTimeouts();
        assertThat(rooms.state(expired.code(),host).status()).isEqualTo("expired");
        mvc.perform(get("/api/quiz/rooms/"+code+"/results").with(user(host).roles("STUDENT"))).andExpect(status().isOk()).andExpect(jsonPath("review.length()").value(5));
        long subject=jdbc.queryForObject("select id from subjects where code='starter-math-sums-v1'",Long.class), grade=jdbc.queryForObject("select id from grades where grade_no=5",Long.class);
        String old=mvc.perform(post("/api/quiz/rooms").with(user(host).roles("STUDENT")).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("subjectId",subject,"gradeId",grade,"questionCount",5,"roundSeconds",20,"maxPlayers",4,"language","ru"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String legacyCode=mapper.readTree(old).path("code").asText();
        assertThat(jdbc.queryForObject("select game_version_id from quiz_rooms where code=?",Long.class,legacyCode)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from quiz_rounds r join quiz_rooms q on q.id=r.room_id where q.code=?",Integer.class,legacyCode)).isEqualTo(5);
        mvc.perform(post("/api/quiz/rooms/"+legacyCode+"/start").with(user(host).roles("STUDENT")).with(csrf())).andExpect(status().isOk());
    }
    @Test void migratedHistoryUsesLegacyRendererSavedRatingAndUnknownTime() throws Exception {
        assertThat(legacy.room("LEGACY0","migration@example.test").rounds().getFirst().body()).isEqualTo("Сұрақ");
        String guest=profile("ru",5);
        Long student=jdbc.queryForObject("select p.id from student_profiles p join app_users u on u.id=p.user_id where u.email=?",Long.class,guest);
        jdbc.update("insert into quiz_participants(room_id,student_profile_id,display_name,ready) values (900003,?,'Исторический игрок',true)",student);
        Long participant=jdbc.queryForObject("select id from quiz_participants where room_id=900003 and student_profile_id=?",Long.class,student);
        // Сохранённый результат с прежними весами и ранней created_at обязан остаться первым, даже без ответа/response_ms.
        jdbc.update("insert into quiz_results(room_id,participant_id,total_questions,correct_answers,score,max_score,percent,created_at) values (900003,?,1,1,2,2,100, timestamp '2000-01-01 00:00:00')",participant);
        var result=legacy.results("LEGACY2","migration@example.test"); assertThat(result.results().getFirst().percent()).isEqualTo(100);
        assertThat(result.results().getFirst().participantId()).isEqualTo(participant);
        assertThat(result.results().getFirst().score()).isEqualByComparingTo("2");
        assertThat(result.results().getFirst().totalResponseMs()).isNull();
        assertThat(jdbc.queryForObject("select response_ms from quiz_answers where id=900003",Long.class)).isNull();
        for(String language:List.of("ru","kk")) mvc.perform(get("/student/quiz/rooms/LEGACY2/results").param("lang",language).with(user("migration@example.test").roles("STUDENT")))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(language.equals("kk")?"жауап беру уақыты сақталмаған":"Время ответа в старых играх не сохранялось")));
        assertThat(mapper.writeValueAsString(result)).contains("\"totalResponseMs\":null");
    }
    @Test void legacyEntryRoomAndErrorsAreLocalizedWithoutChangingApiCodes() throws Exception {
        String host=profile("kk",5);
        for(String language:List.of("ru","kk")) {
            mvc.perform(get("/student/quiz").param("lang",language).with(user(host).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(language.equals("kk")?"Пәнді таңдаңыз":"Выберите предмет")));
            mvc.perform(get("/student/quiz/rooms/LEGACY0").param("lang",language).with(user("migration@example.test").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(language.equals("kk")?"Ұйымдастырушының күту бөлмесі":"Лобби хоста")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("window.quizLegacyMessages")));
            mvc.perform(post("/student/quiz/rooms/join").param("lang",language).param("code","").with(user(host).roles("STUDENT")).with(csrf()))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(language.equals("kk")?"Бөлме кодын енгізіңіз":"Введите код комнаты")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("room_code_required"))));
            mvc.perform(post("/api/quiz/rooms/LEGACY2/ready").param("lang",language).with(user("migration@example.test").roles("STUDENT")).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("error").value("room_not_waiting"))
                .andExpect(jsonPath("message").value(language.equals("kk")?"Бөлме енді ойыншыларды күтіп тұрған жоқ.":"Комната больше не ожидает игроков."));
            mvc.perform(post("/student/quiz/rooms/LEGACY2/ready").param("lang",language).with(user("migration@example.test").roles("STUDENT")).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(view().name("student/quiz-game-error"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("room_not_waiting"))));
        }
    }
    @Test void fallbackHandlesBlankMetadataInSsrAndApiWithoutRelaxingPublication() throws Exception {
        String host=profile("kk",5); var room=rooms.create(host,new QuizGameRoomRequest.Create(QuizStarterContentService.CODES.get(3),null,null,null,UUID.randomUUID().toString()));
        Long version=jdbc.queryForObject("select published_version_id from quiz_games where code=?",Long.class,QuizStarterContentService.CODES.get(3));
        try {
            jdbc.update("update quiz_game_versions set title_kk='   ' where id=?",version);
            assertThat(catalog.read(host,"kk").stream().filter(c->c.code().equals(QuizStarterContentService.CODES.get(3))).findFirst().orElseThrow().title()).isEqualTo("Солнечная система: верю / не верю");
            mvc.perform(get("/api/quiz/rooms/"+room.code()).with(user(host).roles("STUDENT"))).andExpect(status().isOk()).andExpect(jsonPath("gameTitle").value("Солнечная система: верю / не верю"));
            properties.setGameCatalogEnabled(true);
            mvc.perform(get("/student/quiz").param("lang","kk").with(user(host).roles("STUDENT"))).andExpect(content().string(org.hamcrest.Matchers.containsString("Солнечная система: верю / не верю")));
            jdbc.update("update quiz_game_versions set title_ru=' ' where id=?",version);
            assertThat(catalog.read(host,"kk")).noneMatch(c->c.code().equals(QuizStarterContentService.CODES.get(3)));
            mvc.perform(get("/api/quiz/rooms/"+room.code()).with(user(host).roles("STUDENT"))).andExpect(status().isConflict());
            List<String> errors=new TransactionTemplate(manager).execute(s->adminGames.get(jdbc.queryForObject("select id from quiz_games where code=?",Long.class,QuizStarterContentService.CODES.get(3))).errors());
            assertThat(errors).isNotEmpty();
        } finally { jdbc.update("update quiz_game_versions set title_ru=?, title_kk=? where id=?","Солнечная система: верю / не верю","Күн жүйесі: сенемін / сенбеймін",version); }
        assertThat(QuizSnapshotJson.localized(null,"   ","ru")).isEmpty();
        assertThat(QuizSnapshotJson.localized(" ","Қазақша","ru")).isEqualTo("Қазақша");
        var incomplete=new QuizGameForm(); incomplete.setTitleRu("Полное"); incomplete.setTitleKk(" ");
        assertThat(QuizGameValidation.errors(incomplete,Set.of(),true)).anyMatch(v->v.contains("RU/KK"));
    }
    @Test void importRequiresEditorsAndNeverOverwritesPublishedContent() {
        assertThatThrownBy(()->starter.install(reviewer)).isInstanceOf(ContentValidationException.class);
        var original=SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin@damulab.kz",null,List.of()));
        try {
            long before=games.count(); assertThatThrownBy(()->starter.install("admin@damulab.kz")).isInstanceOf(ContentValidationException.class);
            assertThatThrownBy(()->starter.install(reviewer)).isInstanceOf(ContentValidationException.class); assertThat(games.count()).isEqualTo(before);
        } finally { SecurityContextHolder.getContext().setAuthentication(original); }
    }
    @Test void starterCoverageHasReserveAndRejectsYoungerProfiles() {
        String younger=profile("ru",4);
        for(String code:QuizStarterContentService.CODES) {
            List<String> errors=new TransactionTemplate(manager).execute(s->{var game=games.findByCode(code).orElseThrow(); var view=adminGames.get(game.getId()); assertThat(view.content().worst().playable()).isEqualTo(8); assertThat(view.warnings()).isEmpty(); return view.errors();});
            assertThat(errors).isEmpty();
            assertThatThrownBy(()->rooms.create(younger,new QuizGameRoomRequest.Create(code,null,null,null,UUID.randomUUID().toString()))).isInstanceOf(QuizEngineException.class);
        }
    }
    private String profile(String locale,Integer grade) {
        return new TransactionTemplate(manager).execute(s->{String email=UUID.randomUUID()+"@compat.test"; var user=new AppUser(email,"test-only","Ученик",null); user.getRoles().add(roles.findByCode(RoleCode.STUDENT).orElseThrow()); users.save(user); students.save(new StudentProfile(user,grade,locale)); return email;});
    }
    /** Управляемое серверное время только тестового контекста, без marker-файлов и внешних часов. */
    static class TestClock extends Clock {
        long offset;
        /** Возвращает одну серверную зону теста. */
        public ZoneId getZone(){return ZoneOffset.UTC;}
        /** Тест фиксирует UTC и не меняет календарную семантику при запросе зоны. */
        public Clock withZone(ZoneId zone){return this;}
        /** Возвращает фиксированную базу с управляемым сдвигом для игровых окон. */
        public Instant instant(){return Instant.parse("2026-10-10T12:00:00Z").plusSeconds(offset);}
    }
    @TestConfiguration static class TimeConfig { @Bean @Primary TestClock testClock(){return new TestClock();} }
}
