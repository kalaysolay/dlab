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
@SpringBootTest(properties={"damulab.speaking.enabled=true","damulab.speaking.pilot-only=false","damulab.speaking.voice.provider=whisper"})
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
    @SpyBean LocalWhisperTranscriber transcriber;
    @Autowired SpeakingVoiceAttemptRepository voiceAttempts;
    @Autowired SpeakingVoiceStore voiceStore;

    @BeforeEach
    void resetModuleOnly() {
        observations.deleteAll(); turns.deleteAll(); sessions.deleteAll(); usage.deleteAll();
        properties.setEnabled(true); properties.setPilotOnly(false); properties.setAllowedStudentIds(List.of());
        Mockito.reset(tutor);
        Mockito.reset(transcriber);
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
    void summaryCountsRecordingsAndHelpButOnlyIndependentErrors() throws Exception {
        var session=create(UUID.randomUUID());var original=session;
        session=help(session);var repeat=help(original);
        assertThat(repeat.path("version")).isEqualTo(session.path("version"));
        session=answer(session,UUID.randomUUID(),"Yesterday I go to the cinema.");
        voiceResult("Yesterday I go to the cinema.",7);
        var candidate=json(upload(session,UUID.randomUUID(),wavHeader()).andExpect(status().isOk()).andReturn());
        session=json(confirmVoice(session,candidate,UUID.randomUUID(),candidate.path("text").asText()).andExpect(status().isCreated()).andReturn());
        voiceResult("I watched a film.",9);
        candidate=json(upload(session,UUID.randomUUID(),wavHeader()).andExpect(status().isOk()).andReturn());
        session=json(confirmVoice(session,candidate,UUID.randomUUID(),"Yesterday I go to the cinema.").andExpect(status().isCreated()).andReturn());
        session=finish(session);var summary=session.path("summary");
        assertThat(summary.path("acceptedAnswers").asInt()).isEqualTo(3);
        assertThat(summary.path("totalTurns").asInt()).isEqualTo(3);
        assertThat(summary.path("voiceAnswers").asInt()).isEqualTo(2);
        assertThat(summary.path("audioSeconds").asInt()).isEqualTo(16);
        assertThat(summary.path("helpUses").asInt()).isEqualTo(1);
        assertThat(summary.path("difficulties").size()).isEqualTo(1);
        assertThat(summary.path("difficulties").get(0).path("observations").asInt()).isEqualTo(1);
        assertThat(summary.path("nextFocus").isEmpty()).isTrue();
        var voiceNote=session.path("observations").get(1);
        session=dispute(session,voiceNote.path("id").asText());
        assertThat(session.path("summary").path("difficulties").isEmpty()).isTrue();
    }

    @Test
    void focusUsesPriorCompatibleRepeatsAndRecalculatesAfterTtlDisputeAndDeletion() throws Exception {
        var previous=answer(create(UUID.randomUUID()),UUID.randomUUID(),"Yesterday I go to the cinema.");
        previous=finish(answer(previous,UUID.randomUUID(),"Yesterday I go to the cinema."));
        var hotel=json(mvc.perform(post(BASE+"/sessions").with(user(STUDENT).roles("STUDENT")).with(csrf())
                .header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content(createBody().replace("weekend_story","hotel_booking"))).andExpect(status().isCreated()).andReturn());
        assertThat(hotel.path("focusSkills").isEmpty()).isTrue();finish(hotel);
        var next=create(UUID.randomUUID());assertThat(next.path("focusSkills").get(0).asText()).isEqualTo("grammar.past_simple");
        UUID priorId=UUID.fromString(previous.path("id").asText());
        jdbc.update("update speaking_observations set expires_at=? where session_id=?",OffsetDateTime.now().minusMinutes(1),priorId);
        assertThat(reload(next).path("focusSkills").isEmpty()).isTrue();
        jdbc.update("update speaking_observations set expires_at=?,created_at=? where session_id=?",OffsetDateTime.now().plusDays(100),OffsetDateTime.now().minusDays(31),priorId);
        assertThat(reload(next).path("focusSkills").isEmpty()).isTrue();
        jdbc.update("update speaking_observations set created_at=? where session_id=?",OffsetDateTime.now(),priorId);
        assertThat(reload(next).path("focusSkills").size()).isEqualTo(1);
        previous=dispute(previous,previous.path("observations").get(0).path("id").asText());
        assertThat(reload(next).path("focusSkills").isEmpty()).isTrue();
        next=answer(next,UUID.randomUUID(),"Yesterday I go to the cinema.");
        // Текущая практика не превращает своё первое замечание в «ранее повторявшуюся» трудность.
        assertThat(next.path("focusSkills").isEmpty()).isTrue();next=finish(next);
        assertThat(next.path("summary").path("nextFocus").size()).isEqualTo(1);
        mvc.perform(delete(BASE+"/sessions/"+priorId).with(user(STUDENT).roles("STUDENT")).with(csrf())).andExpect(status().isNoContent());
        assertThat(reload(next).path("summary").path("nextFocus").isEmpty()).isTrue();
    }

    @Test
    void focusRanksDistinctTurnsByFrequencyThenFreshnessAndLimitsToTwo() throws Exception {
        var session=create(UUID.randomUUID());
        for(int i=0;i<7;i++)session=answer(session,UUID.randomUUID(),"I buy a apple and a orange in Monday.");
        var stored=sessions.findById(UUID.fromString(session.path("id").asText())).orElseThrow();
        var history=turns.findBySessionIdOrderBySequenceNo(stored.id);var now=OffsetDateTime.now();
        // Два замечания об артиклях в одном ходе дают одно независимое наблюдение.
        seedNote(stored,history.get(0),"grammar.articles","a apple","an apple",now.minusHours(4));
        seedNote(stored,history.get(0),"grammar.articles","a orange","an orange",now.minusHours(4));
        seedNote(stored,history.get(1),"grammar.articles","a apple","an apple",now.minusHours(3));
        seedNote(stored,history.get(2),"grammar.prepositions","in Monday","on Monday",now.minusHours(2));
        seedNote(stored,history.get(3),"grammar.prepositions","in Monday","on Monday",now.minusHours(1));
        for(int i=4;i<7;i++)seedNote(stored,history.get(i),"grammar.past_simple","buy","bought",now.minusHours(5));
        session=finish(session);var next=create(UUID.randomUUID());
        assertThat(next.path("focusSkills").toString()).isEqualTo("[\"grammar.past_simple\",\"grammar.prepositions\"]");
        var difficulties=session.path("summary").path("difficulties");assertThat(difficulties.size()).isEqualTo(3);
        assertThat(difficulties.get(2).path("observations").asInt()).isEqualTo(2);
    }

    @Test
    void deletingAllHistoryIncludesHiddenOlderSessionsButPreservesOtherStudentsAndQuotas() throws Exception {
        var session=answer(create(UUID.randomUUID()),UUID.randomUUID(),"Yesterday I go to the cinema.");
        voiceResult("A draft that must disappear.",4);upload(session,UUID.randomUUID(),wavHeader()).andExpect(status().isOk());
        var template=sessions.findById(UUID.fromString(session.path("id").asText())).orElseThrow();
        for(int i=0;i<22;i++)sessions.saveAndFlush(pastSession(template,template.studentId));
        Long other=students.findByUserEmailIgnoreCase("speaking-other@test.local").orElseThrow().getId();
        var foreign=sessions.saveAndFlush(pastSession(template,other));
        long mastery=jdbc.queryForObject("select count(*) from skill_mastery",Long.class);
        mvc.perform(get(BASE+"/sessions").with(user(STUDENT).roles("STUDENT"))).andExpect(jsonPath("$.length()").value(20));
        mvc.perform(delete(BASE+"/sessions").with(user(STUDENT).roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(delete(BASE+"/sessions").with(user(STUDENT).roles("STUDENT")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete(BASE+"/sessions").with(user(STUDENT).roles("STUDENT")).with(csrf())).andExpect(status().isNoContent());
        assertThat(sessions.findAll()).extracting(s->s.id).containsExactly(foreign.id);
        assertThat(observations.count()).isZero();assertThat(turns.count()).isZero();assertThat(voiceAttempts.count()).isZero();
        assertThat(usage.findAll().get(0).sessionsStarted).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from skill_mastery",Long.class)).isEqualTo(mastery);
        mvc.perform(get(BASE+"/difficulties").with(user(STUDENT).roles("STUDENT"))).andExpect(content().json("[]"));
    }

    @Test
    void historicalSessionsDoNotInventZeroHelpCount() throws Exception {
        var session=create(UUID.randomUUID());
        jdbc.update("update speaking_sessions set help_uses=null where id=?",UUID.fromString(session.path("id").asText()));
        session=finish(help(session));
        assertThat(session.path("summary").path("helpUses").isNull()).isTrue();
        assertThat(session.path("summary").path("audioSeconds").asInt()).isZero();
        assertThat(session.path("summary").path("difficulties").isEmpty()).isTrue();
    }

    private JsonNode help(JsonNode session)throws Exception{return versionPost(session,"help");}
    private JsonNode finish(JsonNode session)throws Exception{return versionPost(session,"finish");}
    private JsonNode dispute(JsonNode session,String id)throws Exception{return versionPost(session,"observations/"+id+"/dispute");}
    private JsonNode versionPost(JsonNode session,String action)throws Exception{
        return json(mvc.perform(post(BASE+"/sessions/"+session.path("id").asText()+"/"+action)
                .with(user(STUDENT).roles("STUDENT")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":"+session.path("version").asLong()+"}")).andExpect(status().isOk()).andReturn());
    }
    private JsonNode reload(JsonNode session)throws Exception{
        return json(mvc.perform(get(BASE+"/sessions/"+session.path("id").asText()).with(user(STUDENT).roles("STUDENT")))
                .andExpect(status().isOk()).andReturn());
    }
    private void seedNote(SpeakingSession session,SpeakingTurn turn,String skill,String original,String corrected,OffsetDateTime when){
        var note=new SpeakingObservation();note.studentId=session.studentId;note.sessionId=session.id;note.turnId=turn.id;
        note.skillCode=skill;note.kind="error";note.evidence="confirmed";note.source="typed";note.original=original;
        note.corrected=corrected;note.explanation="Контрольный факт для проверки счётчиков.";note.createdAt=when;note.expiresAt=when.plusDays(180);
        observations.saveAndFlush(note);
    }
    private SpeakingSession pastSession(SpeakingSession template,Long owner){
        var s=new SpeakingSession();s.studentId=owner;s.createRequestId=UUID.randomUUID();s.scenarioCode=template.scenarioCode;
        s.scenarioSnapshot=template.scenarioSnapshot;s.level=template.level;s.language=template.language;s.status="ENDED";s.activeSlot=null;
        s.createdAt=OffsetDateTime.now().minusDays(1);s.updatedAt=s.createdAt;return s;
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

    @Test
    void transcriptionIsOnlyADraftAndConfirmationSetsVerifiedVoiceSource() throws Exception {
        voiceResult("Yesterday I go to the cinema.",5);
        var session=create(UUID.randomUUID());UUID key=UUID.randomUUID();
        var candidate=json(upload(session,key,wavHeader()).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andReturn());
        assertThat(turns.count()).isZero(); assertThat(observations.count()).isZero();
        assertThat(candidate.path("text").asText()).isEqualTo("Yesterday I go to the cinema.");
        assertThat(json(upload(session,key,wavHeader()).andExpect(status().isOk()).andReturn()).path("id")).isEqualTo(candidate.path("id"));
        upload(session,key,"RIFF0000WAVEdifferent-audio".getBytes(StandardCharsets.US_ASCII)).andExpect(status().isConflict());
        Mockito.verify(transcriber,Mockito.times(1)).transcribe(Mockito.any());
        assertThat(usage.findAll().get(0).audioSeconds).isEqualTo(5);
        UUID answerKey=UUID.randomUUID();
        var accepted=json(confirmVoice(session,candidate,answerKey,candidate.path("text").asText()).andExpect(status().isCreated()).andReturn());
        confirmVoice(session,candidate,answerKey,candidate.path("text").asText()).andExpect(status().isCreated());
        assertThat(accepted.path("turns").get(0).path("source").asText()).isEqualTo("voice");
        assertThat(accepted.path("observations").get(0).path("source").asText()).isEqualTo("voice");
        assertThat(turns.findAll().get(0).audioSeconds).isEqualTo(5);
        assertThat(voiceAttempts.findAll().get(0).transcript).isNull();
        confirmVoice(accepted,candidate,UUID.randomUUID(),candidate.path("text").asText()).andExpect(status().isConflict());
    }

    @Test
    void editedTranscriptIsExcludedFromIndependentDifficultyAndClientCannotForgeSource() throws Exception {
        voiceResult("Yesterday I went to the cinema.",4);
        var session=create(UUID.randomUUID());
        var candidate=json(upload(session,UUID.randomUUID(),wavHeader()).andExpect(status().isOk()).andReturn());
        var accepted=json(confirmVoice(session,candidate,UUID.randomUUID(),"Yesterday I go to the cinema.")
                .andExpect(status().isCreated()).andReturn());
        assertThat(accepted.path("turns").get(0).path("source").asText()).isEqualTo("edited");
        assertThat(observations.findAll().get(0).source).isEqualTo("edited");
        mvc.perform(get(BASE+"/difficulties").with(user(STUDENT).roles("STUDENT"))).andExpect(content().json("[]"));
        confirmVoice(accepted,mapper.readTree("{\"id\":\""+UUID.randomUUID()+"\"}"),UUID.randomUUID(),"Forged.")
                .andExpect(status().isNotFound());
    }

    @Test
    void voiceChecksOwnershipCsrfDisabledModeAndByteLimitBeforeCallingAsr() throws Exception {
        voiceResult("A real test transcript.",3);var session=create(UUID.randomUUID());
        String path=BASE+"/sessions/"+session.path("id").asText()+"/transcriptions?expectedVersion=0";
        mvc.perform(post(path).with(user(STUDENT).roles("STUDENT")).header("Idempotency-Key",UUID.randomUUID())
                .content(wavHeader())).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user("speaking-other@test.local").roles("STUDENT")).with(csrf())
                .header("Idempotency-Key",UUID.randomUUID()).content(wavHeader())).andExpect(status().isNotFound());
        upload(session,UUID.randomUUID(),new byte[12]).andExpect(status().isUnsupportedMediaType());
        upload(session,UUID.randomUUID(),new byte[SpeakingVoiceService.MAX_BYTES+1]).andExpect(status().isPayloadTooLarge());
        properties.setEnabled(false);upload(session,UUID.randomUUID(),wavHeader()).andExpect(status().isNotFound());
        properties.setEnabled(true);Mockito.doReturn(false).when(transcriber).available();
        upload(session,UUID.randomUUID(),wavHeader()).andExpect(status().isServiceUnavailable());
        Mockito.verify(transcriber,Mockito.never()).transcribe(Mockito.any());
    }

    @Test
    void failedAsrDoesNotAcceptTurnAndKeepsBudgetAfterHistoryDeletion() throws Exception {
        voiceResult("Unused.",3);var session=create(UUID.randomUUID());
        Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT,
                "speaking_voice_timeout")).when(transcriber).transcribe(Mockito.any());
        upload(session,UUID.randomUUID(),wavHeader()).andExpect(status().isGatewayTimeout());
        assertThat(voiceAttempts.findAll().get(0).state).isEqualTo("FAILED");
        assertThat(voiceAttempts.findAll().get(0).activeSlot).isNull();
        assertThat(turns.count()).isZero();assertThat(observations.count()).isZero();
        assertThat(usage.findAll().get(0).audioSeconds).isEqualTo(60);
        mvc.perform(delete(BASE+"/sessions/"+session.path("id").asText()).with(user(STUDENT).roles("STUDENT")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(voiceAttempts.count()).isZero();assertThat(usage.findAll().get(0).audioSeconds).isEqualTo(60);
    }

    @Test
    void staleQuestionAndDiscardedOrExpiredDraftCannotBeConfirmed() throws Exception {
        voiceResult("I watched a film.",3);var session=create(UUID.randomUUID());
        var candidate=json(upload(session,UUID.randomUUID(),wavHeader()).andExpect(status().isOk()).andReturn());
        mvc.perform(delete(BASE+"/sessions/"+session.path("id").asText()+"/transcriptions/"+candidate.path("id").asText())
                .with(user("speaking-other@test.local").roles("STUDENT")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete(BASE+"/sessions/"+session.path("id").asText()+"/transcriptions/"+candidate.path("id").asText())
                .with(user(STUDENT).roles("STUDENT")).with(csrf())).andExpect(status().isNoContent());
        confirmVoice(session,candidate,UUID.randomUUID(),"I watched a film.").andExpect(status().isConflict());
        candidate=json(upload(session,UUID.randomUUID(),wavHeader()).andExpect(status().isOk()).andReturn());
        jdbc.update("update speaking_voice_attempts set expires_at=?",OffsetDateTime.now().minusSeconds(1));
        confirmVoice(session,candidate,UUID.randomUUID(),"I watched a film.").andExpect(status().isConflict());
        properties.setEnabled(false);voiceStore.purge();assertThat(voiceAttempts.count()).isZero();properties.setEnabled(true);
        candidate=json(upload(session,UUID.randomUUID(),wavHeader()).andExpect(status().isOk()).andReturn());
        var accepted=answer(session,UUID.randomUUID(),"A typed answer.");
        confirmVoice(accepted,candidate,UUID.randomUUID(),"I watched a film.").andExpect(status().isConflict());
        assertThat(turns.count()).isEqualTo(1);
    }

    @Test
    void voiceBudgetAndMinuteRateAreServerSideAndSurviveDeletionOfDrafts() throws Exception {
        voiceResult("I watched a film.",3);var session=create(UUID.randomUUID());
        for(int i=0;i<6;i++)upload(session,UUID.randomUUID(),wavHeader()).andExpect(status().isOk());
        upload(session,UUID.randomUUID(),wavHeader()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("speaking_voice_rate_limit"));
        voiceAttempts.deleteAll();jdbc.update("update speaking_daily_usage set audio_seconds=900,voice_requests=0");
        upload(session,UUID.randomUUID(),wavHeader()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("speaking_audio_daily_limit"));
    }

    @Test
    void slowRecognitionHoldsNoTransactionAndLateResultIsDiscardedWhenQuestionChanged() throws Exception {
        voiceResult("I watched a film.",3);var session=create(UUID.randomUUID());
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        Mockito.doAnswer(call->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            entered.countDown();assertThat(release.await(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return new SpeakingTranscriber.Transcript("I watched a film.",3);
        }).when(transcriber).transcribe(Mockito.any());
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var result=executor.submit(()->upload(session,UUID.randomUUID(),wavHeader()).andReturn().getResponse().getStatus());
            try {
                assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                upload(session,UUID.randomUUID(),wavHeader()).andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("speaking_voice_processing"));
                // Если ASR держит профиль/транзакцию, этот независимый ответ зависнет до release.
                answer(session,UUID.randomUUID(),"Another answer.");
            } finally { release.countDown(); }
            assertThat(result.get(15,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(409);
        }
        assertThat(voiceAttempts.findAll().get(0).state).isEqualTo("FAILED");
        assertThat(voiceAttempts.findAll().get(0).transcript).isNull();assertThat(turns.count()).isEqualTo(1);
    }

    private void voiceResult(String text,int seconds) {
        // Тестовый double проверяет контракт хранения; настоящее распознавание проверяется отдельно на локальном WAV.
        Mockito.doReturn(true).when(transcriber).available();
        Mockito.doAnswer(call->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new SpeakingTranscriber.Transcript(text,seconds);
        }).when(transcriber).transcribe(Mockito.any());
    }
    private byte[] wavHeader() { return "RIFF0000WAVEtest-audio-payload".getBytes(StandardCharsets.US_ASCII); }
    private org.springframework.test.web.servlet.ResultActions upload(JsonNode session,UUID key,byte[] audio) throws Exception {
        return mvc.perform(post(BASE+"/sessions/"+session.path("id").asText()+"/transcriptions")
                .param("expectedVersion",session.path("version").asText()).with(user(STUDENT).roles("STUDENT")).with(csrf())
                .header("Idempotency-Key",key).contentType("audio/wav").content(audio));
    }
    private org.springframework.test.web.servlet.ResultActions confirmVoice(JsonNode session,JsonNode candidate,UUID key,String text) throws Exception {
        return mvc.perform(post(BASE+"/sessions/"+session.path("id").asText()+"/turns").with(user(STUDENT).roles("STUDENT")).with(csrf())
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SpeakingViews.AnswerRequest(session.path("version").asLong(),text,
                        UUID.fromString(candidate.path("id").asText())))));
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
                .content(mapper.writeValueAsString(new SpeakingViews.AnswerRequest(session.path("version").asLong(),text,null))));
    }
    private JsonNode json(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)); }
}
