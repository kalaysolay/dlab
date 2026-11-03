package kz.damulab.speaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import kz.damulab.users.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Сквозная проверка модульного хранения, безопасности и повторных запросов без сети и платных API. */
@SpringBootTest(properties={"damulab.speaking.enabled=true","damulab.speaking.pilot-only=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class SpeakingIntegrationTest {
    private static final String BASE="/api/student/speaking";
    private static final String STUDENT="student@damulab.kz";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SpeakingSessionRepository sessions;
    @Autowired SpeakingTurnRepository turns;
    @Autowired SpeakingObservationRepository observations;
    @Autowired SpeakingDailyUsageRepository usage;
    @Autowired SpeakingProperties properties;
    @Autowired SpeakingScenarioCatalog catalog;
    @Autowired StudentProfileRepository students;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired SpeakingMaintenance maintenance;
    @SpyBean StubSpeakingTutor tutor;

    @BeforeEach
    void resetModuleOnly() {
        observations.deleteAll(); turns.deleteAll(); sessions.deleteAll(); usage.deleteAll();
        properties.setEnabled(true); properties.setPilotOnly(false); properties.setAllowedStudentIds(List.of());
        Mockito.reset(tutor);
        if(students.findByUserEmailIgnoreCase("speaking-other@test.local").isEmpty()) {
            var other=users.save(new AppUser("speaking-other@test.local","unused","Speaking test",null));
            students.save(new StudentProfile(other,6,"ru"));
        }
    }

    @Test
    void initialQuestionNeedsNoLearnerInputAndSurvivesReload() throws Exception {
        var session=create(UUID.randomUUID());
        assertThat(session.path("firstQuestion").asText()).contains("weekend");
        assertThat(session.path("openingText").asText()).startsWith("Hi!");
        assertThat(session.path("turns").size()).isZero();
        assertThat(session.path("providerMode").asText()).isEqualTo("STUB");
        var reloaded=json(mvc.perform(get(BASE+"/sessions/"+session.path("id").asText()).with(user(STUDENT).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn());
        assertThat(reloaded.path("firstQuestion")).isEqualTo(session.path("firstQuestion"));
        mvc.perform(get("/student/speaking").with(user(STUDENT).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("/js/speaking.js")));
    }

    @Test
    void duplicateRequestsAreIdempotentButChangedBodiesConflict() throws Exception {
        UUID createKey=UUID.randomUUID();
        var session=create(createKey);
        assertThat(create(createKey).path("id")).isEqualTo(session.path("id"));
        UUID turnKey=UUID.randomUUID();
        var updated=answer(session,turnKey,"Yesterday I go to the cinema.");
        answer(session,turnKey,"Yesterday I go to the cinema.");
        assertThat(sessions.count()).isEqualTo(1);
        assertThat(turns.count()).isEqualTo(1);
        assertThat(observations.count()).isEqualTo(1);
        assertThat(updated.path("observations").get(0).path("skillCode").asText()).isEqualTo("grammar.past_simple");
        assertThat(updated.path("turns").get(0).path("source").asText()).isEqualTo("typed");
        postAnswer(session,turnKey,"A different answer.").andExpect(status().isConflict());
        postAnswer(session,UUID.randomUUID(),"Another answer.").andExpect(status().isConflict());
        assertThat(turns.count()).isEqualTo(1);
    }

    @Test
    void correctionsAreAtomicAndNeverPolluteSchoolAnalytics() throws Exception {
        long mastery=jdbc.queryForObject("select count(*) from skill_mastery",Long.class);
        var session=create(UUID.randomUUID());
        Mockito.doReturn(new SpeakingTutor.Reply("Thanks.","Next?",List.of(
                new SpeakingTutor.Feedback("invented.skill","not in learner text","fixed","explanation"))))
                .when(tutor).answer(Mockito.any(),Mockito.anyInt(),Mockito.anyString(),Mockito.anyString());
        postAnswer(session,UUID.randomUUID(),"Yesterday I go to the cinema.").andExpect(status().isServiceUnavailable());
        assertThat(turns.count()).isZero();
        assertThat(observations.count()).isZero();
        assertThat(sessions.findAll().get(0).acceptedTurns).isZero();
        Mockito.reset(tutor);
        var accepted=answer(session,UUID.randomUUID(),"Yesterday I go to the cinema.");
        assertThat(accepted.path("acceptedTurns").asInt()).isEqualTo(1);
        assertThat(observations.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from skill_mastery",Long.class)).isEqualTo(mastery);
    }

    @Test
    void repeatsAreVisibleButDisputeAndDeleteImmediatelyRemoveThem() throws Exception {
        var session=answer(create(UUID.randomUUID()),UUID.randomUUID(),"Yesterday I go to the cinema.");
        session=answer(session,UUID.randomUUID(),"Yesterday I go to the cinema.");
        mvc.perform(get(BASE+"/difficulties").with(user(STUDENT).roles("STUDENT")))
                .andExpect(jsonPath("$[0].status").value("recurring")).andExpect(jsonPath("$[0].observations").value(2));
        String id=session.path("id").asText();
        mvc.perform(post(BASE+"/sessions/"+id+"/observations/"+session.path("observations").get(0).path("id").asText()+"/dispute")
                        .with(user(STUDENT).roles("STUDENT")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":"+session.path("version").asLong()+"}"))
                .andExpect(status().isOk());
        mvc.perform(get(BASE+"/difficulties").with(user(STUDENT).roles("STUDENT")))
                .andExpect(jsonPath("$[0].status").value("observed")).andExpect(jsonPath("$[0].observations").value(1));
        mvc.perform(delete(BASE+"/sessions/"+id).with(user(STUDENT).roles("STUDENT")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(observations.count()).isZero(); assertThat(turns.count()).isZero();
        mvc.perform(get(BASE+"/difficulties").with(user(STUDENT).roles("STUDENT"))).andExpect(content().json("[]"));
    }

    @Test
    void roleCsrfOwnerFlagsAndPilotAreEnforcedForDirectApi() throws Exception {
        mvc.perform(get(BASE+"/scenarios").with(user("parent@damulab.kz").roles("PARENT"))).andExpect(status().isForbidden());
        mvc.perform(post(BASE+"/sessions").with(user(STUDENT).roles("STUDENT"))
                .header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(createBody()))
                .andExpect(status().isForbidden());
        var session=create(UUID.randomUUID());
        String id=session.path("id").asText();
        mvc.perform(get(BASE+"/sessions/"+id).with(user("speaking-other@test.local").roles("STUDENT")))
                .andExpect(status().isNotFound());
        mvc.perform(delete(BASE+"/sessions/"+id).with(user("speaking-other@test.local").roles("STUDENT")).with(csrf()))
                .andExpect(status().isNotFound());
        properties.setEnabled(false);
        mvc.perform(get(BASE+"/sessions/"+id).with(user(STUDENT).roles("STUDENT"))).andExpect(status().isNotFound());
        properties.setEnabled(true);properties.setPilotOnly(true);
        mvc.perform(get(BASE+"/scenarios").with(user(STUDENT).roles("STUDENT"))).andExpect(status().isForbidden());
        properties.setAllowedStudentIds(List.of(students.findByUserEmailIgnoreCase(STUDENT).orElseThrow().getId()));
        mvc.perform(get(BASE+"/scenarios").with(user(STUDENT).roles("STUDENT"))).andExpect(status().isOk());
    }

    @Test
    void allTurnsRemainInOrderAndEightAnswersCompleteSession() throws Exception {
        var session=create(UUID.randomUUID());
        for(int i=0;i<8;i++)session=answer(session,UUID.randomUUID(),"An ordinary answer "+i+".");
        assertThat(session.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(session.path("turns").size()).isEqualTo(8);
        assertThat(session.path("observations").size()).isZero();
        assertThat(session.path("turns").get(0).path("text").asText()).isEqualTo("An ordinary answer 0.");
        assertThat(session.path("turns").get(7).path("questionText").asText()).isEmpty();
        postAnswer(session,UUID.randomUUID(),"One more.").andExpect(status().isConflict());
        assertThat(turns.count()).isEqualTo(8);
    }

    @Test
    void quotaCannotBeResetByDeletingConversations() throws Exception {
        for(int i=0;i<3;i++){
            var session=create(UUID.randomUUID());
            mvc.perform(delete(BASE+"/sessions/"+session.path("id").asText()).with(user(STUDENT).roles("STUDENT")).with(csrf()))
                    .andExpect(status().isNoContent());
        }
        createCall(UUID.randomUUID()).andExpect(status().isTooManyRequests());
        assertThat(sessions.count()).isZero(); assertThat(usage.findAll().get(0).sessionsStarted).isEqualTo(3);
    }

    @Test
    void idleSessionExpiresAndScenarioIsFrozenAtCreation() throws Exception {
        var session=create(UUID.randomUUID());
        var questions=catalog.find("weekend_story").questions();
        String original=questions.get(1);
        try {
            questions.set(1,"Changed after creation?");
            var updated=answer(session,UUID.randomUUID(),"I watched a film.");
            assertThat(updated.path("turns").get(0).path("questionText").asText()).isEqualTo(original);
        } finally { questions.set(1,original); }
        jdbc.update("update speaking_sessions set updated_at=? where id=?",OffsetDateTime.now().minusMinutes(31),UUID.fromString(session.path("id").asText()));
        mvc.perform(get(BASE+"/sessions/"+session.path("id").asText()).with(user(STUDENT).roles("STUDENT")))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        create(UUID.randomUUID());
    }

    @Test
    void dashboardPlacesModuleBetweenLessonsAndTranslator() throws Exception {
        String html=mvc.perform(get("/student").with(user(STUDENT).roles("STUDENT"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html.indexOf("href=\"/student/speaking\"")).isBetween(
                html.indexOf("href=\"/student/lectures\"")+1,html.indexOf("href=\"/student/translator\"")-1);
    }

    @Test
    void helpIsRememberedButDoesNotCountAsIndependentError() throws Exception {
        var session=create(UUID.randomUUID());
        var helped=json(mvc.perform(post(BASE+"/sessions/"+session.path("id").asText()+"/help")
                        .with(user(STUDENT).roles("STUDENT")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":"+session.path("version").asLong()+"}"))
                .andExpect(status().isOk()).andReturn());
        var accepted=answer(helped,UUID.randomUUID(),"Yesterday I go to the cinema.");
        assertThat(accepted.path("observations").get(0).path("assisted").asBoolean()).isTrue();
        mvc.perform(get(BASE+"/difficulties").with(user(STUDENT).roles("STUDENT"))).andExpect(content().json("[]"));
        assertThat(observations.count()).isEqualTo(1);
    }

    @Test
    void retentionClearsExpiredTextAndObservationsEvenWhenUiDisabled() throws Exception {
        var session=answer(create(UUID.randomUUID()),UUID.randomUUID(),"Yesterday I go to the cinema.");
        UUID id=UUID.fromString(session.path("id").asText());
        jdbc.update("update speaking_turns set created_at=? where session_id=?",OffsetDateTime.now().minusDays(31),id);
        jdbc.update("update speaking_observations set expires_at=? where session_id=?",OffsetDateTime.now().minusMinutes(1),id);
        properties.setEnabled(false);
        maintenance.purge();
        assertThat(observations.count()).isZero();
        assertThat(turns.findAll().get(0).learnerText).isEmpty();
        assertThat(sessions.count()).isEqualTo(1);
    }

    @Test
    void concurrentRetriesAcceptOneTurnAndOneObservation() throws Exception {
        var session=create(UUID.randomUUID());UUID key=UUID.randomUUID();
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> send=()->{
                start.await();return postAnswer(session,key,"Yesterday I go to the cinema.")
                        .andReturn().getResponse().getStatus();
            };
            var first=executor.submit(send);var second=executor.submit(send);start.countDown();
            assertThat(first.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(201);
            assertThat(second.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(201);
        }
        assertThat(turns.count()).isEqualTo(1);assertThat(observations.count()).isEqualTo(1);
    }

    private JsonNode create(UUID key) throws Exception { return json(createCall(key).andExpect(status().isCreated()).andReturn()); }
    private org.springframework.test.web.servlet.ResultActions createCall(UUID key) throws Exception {
        return mvc.perform(post(BASE+"/sessions").with(user(STUDENT).roles("STUDENT")).with(csrf())
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(createBody()));
    }
    private String createBody() { return "{\"scenarioCode\":\"weekend_story\",\"level\":\"B1\",\"explanationLanguage\":\"ru\"}"; }
    private JsonNode answer(JsonNode session,UUID key,String text) throws Exception {
        return json(postAnswer(session,key,text).andExpect(status().isCreated()).andReturn());
    }
    private org.springframework.test.web.servlet.ResultActions postAnswer(JsonNode session,UUID key,String text) throws Exception {
        return mvc.perform(post(BASE+"/sessions/"+session.path("id").asText()+"/turns").with(user(STUDENT).roles("STUDENT")).with(csrf())
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SpeakingViews.AnswerRequest(session.path("version").asLong(),text))));
    }
    private JsonNode json(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)); }
}
