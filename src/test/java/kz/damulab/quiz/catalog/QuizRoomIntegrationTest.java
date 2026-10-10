package kz.damulab.quiz.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.*;
import kz.damulab.quiz.*;
import kz.damulab.quiz.engine.*;
import kz.damulab.users.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.messaging.*;
import org.springframework.messaging.support.*;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

/** Реальные commit/конкуренция новых комнат на H2/PostgreSQL; только синтетические профили @room.test. */
@AutoConfigureMockMvc
@Import(QuizRoomIntegrationTest.TimeConfig.class)
class QuizRoomIntegrationTest extends QuizGameTestSupport {
    @Autowired QuizRoomService rooms;
    @Autowired QuizRoomRepository roomRepository;
    @Autowired QuizRoundRepository roundRepository;
    @Autowired QuizParticipantRepository participantRepository;
    @Autowired QuizAnswerRepository answerRepository;
    @Autowired QuizResultRepository resultRepository;
    @Autowired StudentProfileRepository profiles;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper mapper;
    @Autowired MutableClock clock;
    @Autowired MockMvc mvc;
    @Autowired QuizRoomSubscriptions subscriptions;
    @Autowired QuizRoomEventPublisher publisher;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("clientInboundChannel") org.springframework.messaging.support.AbstractSubscribableChannel inbound;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("clientOutboundChannel") org.springframework.messaging.support.AbstractSubscribableChannel outbound;
    @Autowired org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler broker;
    @SpyBean QuizRoomCode generator;
    @SpyBean FactRoundProvider factProvider;
    @SpyBean QuizRoundFactory factory;
    private final Set<Long> createdGames = new HashSet<>();
    private final List<String> createdRooms = new ArrayList<>();
    private static final Instant BASE = Instant.parse("2026-10-10T12:00:00Z");

    @BeforeEach void resetTime() { clock.set(BASE); }
    @AfterEach void closeFixtures() {
        reset(generator, factProvider, factory); clock.set(BASE.plusSeconds(86400));
        for (String code : createdRooms) rooms.tick(code);
        tx(() -> {
            // Committed фикстуры не удаляются; архив собственных игр сохраняет историю и изоляцию повторного PG suite.
            var ownRooms = entityManager.createQuery("select r from QuizRoom r join r.hostStudentProfile p join p.user u where u.email like :email", QuizRoom.class)
                    .setParameter("email", "%@room.test").getResultList();
            Set<Long> ownGames = new HashSet<>(createdGames);
            for (var r : ownRooms) {
                if (r.getStatus() == QuizRoomStatus.WAITING) r.cancel(OffsetDateTime.now(clock));
                if (r.getGame() != null) ownGames.add(r.getGame().getId());
            }
            // Также архивируем только собственные синтетические банки: иначе они влияют на cross-set диагностику следующего PG прогона.
            if (!ownGames.isEmpty()) {
                for (var c : entityManager.createQuery("select c from QuizWordGameConfig c where c.gameVersion.game.id in :ids", QuizWordGameConfig.class).setParameter("ids", ownGames).getResultList())
                    if (c.getVocabularySet().getStatus() != EditorialStatus.ARCHIVED) vocabulary.archiveSet(c.getVocabularySet().getId(), admin());
                for (var c : entityManager.createQuery("select c from QuizFactGameConfig c where c.gameVersion.game.id in :ids", QuizFactGameConfig.class).setParameter("ids", ownGames).getResultList())
                    if (c.getFactSet().getStatus() != EditorialStatus.ARCHIVED) facts.archiveSet(c.getFactSet().getId(), admin());
            }
            for (Long id : ownGames) if (gameRepository.findById(id).orElseThrow().getStatus() != EditorialStatus.ARCHIVED)
                games.archive(id, revision(id), admin());
            return null;
        });
    }
    @Test void createPinsVersionIsIdempotentAndDoesNotCreateRounds() {
        var game = game(4, 2); var host = profile(5, "kk"); var request = createRequest(game, "once");
        var first = create(host, request); var retry = rooms.create(host, request);
        assertThat(retry.id()).isEqualTo(first.id()); assertThat(first.language()).isEqualTo("kk");
        assertThat(first.participants()).hasSize(1).allMatch(QuizGameRoomResponse.Member::ready);
        assertThat(first.canStart()).isTrue(); assertThat(first.expiresAt()).isEqualTo(OffsetDateTime.ofInstant(BASE.plusSeconds(1800), ZoneOffset.UTC));
        assertThat(roundRepository.countByRoomId(first.id())).isZero();
        failure(() -> rooms.create(host, new QuizGameRoomRequest.Create(game, null, 1, null, "once")), "IDEMPOTENCY_CONFLICT");
        tx(() -> { var g = gameRepository.findByCode(game).orElseThrow(); games.setVisibility(g.getId(), revision(g.getId()), false, admin()); return null; });
        assertThat(rooms.create(host, request).id()).isEqualTo(first.id());
        failure(() -> rooms.create(host, createRequest(game, "hidden")), "GAME_NOT_FOUND");
        assertThat(rooms.start(first.code(), host).status()).isEqualTo("active");
    }
    @ParameterizedTest @EnumSource(kz.damulab.questions.QuestionType.class)
    void subjectRoomAllFormatsAcceptCanonicalRetryAndPreserveHistory(kz.damulab.questions.QuestionType type) throws Exception {
        String host = profile(5, "kk");
        String game = tx(() -> {
            var subject = subjects.save(new kz.damulab.content.Subject("room-" + UUID.randomUUID(), "Предмет", "Пән", "Описание", "Сипаттама"));
            var grade = grades.findAllByOrderByGradeNoAsc().stream().filter(g -> g.getGradeNo() == 5).findFirst().orElseThrow();
            var source = subjectQuestion(subject, type, 2, List.of(grade.getId()));
            String options = switch (type) {
                case SCQ, MCQ -> "[{\"label\":\"A\",\"textRu\":\"Первый\",\"textKk\":\"Бірінші\"},{\"label\":\"B\",\"textRu\":\"Второй\",\"textKk\":\"Екінші\"}]";
                case MATCHING -> "[{\"leftRu\":\"1\",\"leftKk\":\"1\",\"rightRu\":\"Один\",\"rightKk\":\"Бір\"},{\"leftRu\":\"2\",\"leftKk\":\"2\",\"rightRu\":\"Два\",\"rightKk\":\"Екі\"}]";
                case FILL_IN -> "[]";
            };
            String key = switch (type) { case SCQ -> "[\"B\"]"; case MCQ -> "[\"A\",\"B\"]";
                case MATCHING -> "{\"pairs\":[{\"left\":\"1\",\"right\":\"Один\"},{\"left\":\"2\",\"right\":\"Два\"}]}";
                case FILL_IN -> "{\"[[1]]\":{\"answer\":\"30\",\"mode\":\"EXACT\"}}";
            };
            source.replaceContent(new kz.damulab.questions.QuestionVersion(source.getQuestion(), 1, type, subject, null, 2,
                    "Вопрос", "Сұрақ", "Разбор", "Түсіндірме", null, null, "Учебник", options, key));
            entityManager.flush();
            var f = form(QuizGameType.SUBJECT_QUIZ); f.setSubjectId(subject.getId()); f.setQuestionTypes(Set.of(type)); f.setDifficulty(2); f.setAudiencePolicy(AudiencePolicy.FIXED_GRADES); f.setGradeIds(List.of(grade.getId()));
            return remember(publishGame(f));
        });
        var room = create(host, createRequest(game, "subject")); var active = rooms.start(room.code(), host);
        assertThat(active.subjectId()).isNotNull(); assertThat(active.currentRound().prompt()).isEqualTo("Сұрақ");
        String raw = switch (type) { case SCQ -> "{\"selected\":[\"B\"]}"; case MCQ -> "{\"selected\":[\"B\",\"A\"]}";
            case MATCHING -> "{\"pairs\":{\"1\":\"Один\",\"2\":\"Два\"}}"; case FILL_IN -> "{\"answers\":{\"[[1]]\":\"30\"}}"; };
        var answer = new QuizGameRoomRequest.Answer(active.activeRoundId(), mapper.readTree(raw), false); rooms.answer(room.code(), host, answer);
        JsonNode retry = mapper.readTree(type == kz.damulab.questions.QuestionType.MCQ ? "{\"selected\":[\"A\",\"B\"]}" : raw);
        rooms.answer(room.code(), host, new QuizGameRoomRequest.Answer(active.activeRoundId(), retry, false));
        tx(() -> { var source = roundRepository.findById(active.activeRoundId()).orElseThrow().getQuestionVersion(); source.getQuestion().changeStatus(kz.damulab.questions.QuestionStatus.ARCHIVED); return null; });
        clock.set(BASE.plusSeconds(20)); assertThat(rooms.results(room.code(), host).me().correctAnswers()).isEqualTo(1);
    }
    @Test void wordRoomUsesTranslationSnapshotAndCurrentPublishedReplay() throws Exception {
        String host = profile(null, "ru"); String game = tx(() -> {
            var f = form(QuizGameType.WORD_TRANSLATION); f.setVocabularySetId(wordSet(4).getId()); f.setSourceLanguage("en"); f.setTargetLanguage("ru"); return remember(publishGame(f));
        });
        var room = create(host, createRequest(game, "word")); var active = rooms.start(room.code(), host);
        assertThat(active.currentRound().options()).hasSize(4); assertThat(active.contentKind()).isEqualTo("VOCABULARY"); assertThat(active.currentRound().contentKind()).isEqualTo("VOCABULARY");
        var key = tx(() -> roundRepository.findById(active.activeRoundId()).orElseThrow().getAnswerKeySnapshotJson());
        rooms.answer(room.code(), host, new QuizGameRoomRequest.Answer(active.activeRoundId(), mapper.readTree("{\"selected\":[" + key + "]}"), false));
        clock.set(BASE.plusSeconds(20)); assertThat(rooms.results(room.code(), host).me().score()).isEqualByComparingTo("1");
        String pinned = tx(() -> roomRepository.findByCodeIgnoreCase(room.code()).orElseThrow().getGameVersion().getId().toString());
        tx(() -> { var g = gameRepository.findByCode(game).orElseThrow(); var f = games.get(g.getId()).form(); f.setTitleRu("Новая игра"); games.saveDraft(g.getId(), f, admin()); approve(g.getId()); games.publish(g.getId(), revision(g.getId()), false, admin()); return null; });
        var replay = create(host, createRequest(game, "replay"));
        assertThat(replay.gameTitle()).isEqualTo("Новая игра"); assertThat(tx(() -> roomRepository.findByCodeIgnoreCase(replay.code()).orElseThrow().getGameVersion().getId().toString())).isNotEqualTo(pinned);
        assertThat(rooms.results(room.code(), host).me().correctAnswers()).isEqualTo(1);
    }
    @Test void soloUsesFullScheduleFalseRetrySkipAndTimeout() {
        var host = profile(5, "ru"); var room = create(host, createRequest(game(1, 3), "solo"));
        var active = rooms.start(room.code(), host); Long id = active.activeRoundId();
        var falseAnswer = new QuizGameRoomRequest.Answer(id, mapper.getNodeFactory().booleanNode(false), false);
        var accepted = rooms.answer(room.code(), host, falseAnswer);
        assertThat(accepted.currentRound().answered()).isTrue(); assertThat(accepted.status()).isEqualTo("active");
        assertThat(rooms.answer(room.code(), host, falseAnswer).currentRound().answered()).isTrue();
        failure(() -> rooms.answer(room.code(), host, new QuizGameRoomRequest.Answer(id, mapper.getNodeFactory().booleanNode(true), false)), "ANSWER_ALREADY_SUBMITTED");
        failure(() -> rooms.results(room.code(), host), "RESULTS_NOT_AVAILABLE");
        clock.set(BASE.plusSeconds(20)); var next = rooms.state(room.code(), host);
        rooms.answer(room.code(), host, new QuizGameRoomRequest.Answer(next.activeRoundId(), null, true));
        clock.set(BASE.plusSeconds(60)); rooms.enforceTimeouts(); rooms.tick(room.code());
        var result = rooms.results(room.code(), host);
        assertThat(result.me().correctAnswers()).isEqualTo(1); assertThat(result.me().percent()).isEqualTo(33);
        assertThat(result.me().totalResponseMs()).isEqualTo(40000); assertThat(result.review()).hasSize(3);
        assertThat(result.review().get(0).answer().booleanValue()).isFalse();
        assertThat(result.review().get(1).skipped()).isTrue(); assertThat(result.review().get(2).timedOut()).isTrue();
        assertThat(resultRepository.findByRoomIdOrderByScoreDescPercentDescCreatedAtAsc(room.id())).hasSize(1);
        assertThat(rooms.answer(room.code(), host, falseAnswer).status()).isEqualTo("finished");
    }
    @Test void waitingGuestReadySettingsResetLeaveAndHostCancel() {
        var host = profile(5, "ru"); var guest = profile(4, "kk"); var room = create(host, createRequest(game(4, 2), "lobby"));
        var joined = rooms.join(" " + room.code().toLowerCase(Locale.ROOT) + " ", guest);
        assertThat(joined.participants()).hasSize(2); assertThat(joined.canStart()).isFalse();
        failure(() -> rooms.start(room.code(), host), "PARTICIPANT_NOT_READY");
        failure(() -> rooms.start(room.code(), guest), "HOST_ONLY_ACTION");
        rooms.ready(room.code(), guest, new QuizGameRoomRequest.Ready(true, joined.settingsRevision()));
        var changed = rooms.settings(room.code(), host, new QuizGameRoomRequest.Settings(null, 1, 10, joined.settingsRevision()));
        assertThat(changed.settingsRevision()).isEqualTo(joined.settingsRevision() + 1);
        assertThat(changed.participants().stream().filter(p -> !p.host())).allMatch(p -> !p.ready());
        failure(() -> rooms.ready(room.code(), guest, new QuizGameRoomRequest.Ready(true, joined.settingsRevision())), "SETTINGS_REVISION_CONFLICT");
        failure(() -> rooms.settings(room.code(), guest, new QuizGameRoomRequest.Settings(null, 1, 10, changed.settingsRevision())), "HOST_ONLY_ACTION");
        rooms.leave(room.code(), guest); rooms.leave(room.code(), guest);
        failure(() -> rooms.state(room.code(), guest), "PARTICIPANT_REQUIRED");
        failure(() -> rooms.leave(room.code(), host), "GUEST_ONLY_ACTION");
        assertThat(rooms.cancel(room.code(), host).status()).isEqualTo("cancelled");
        assertThat(rooms.cancel(room.code(), host).status()).isEqualTo("cancelled");
        failure(() -> rooms.join(room.code(), guest), "ROOM_CANCELLED");
        failure(() -> rooms.start(room.code(), host), "ROOM_CANCELLED");
    }
    @Test void expiryBoundaryAndRecoveryDoNotDeleteHistory() {
        var host = profile(5, "ru"); var room = create(host, createRequest(game(1, 1), "expiry"));
        clock.set(BASE.plusSeconds(1800));
        assertThat(rooms.preview(room.code(), profile(4, "ru")).joinBlocker()).isEqualTo("ROOM_EXPIRED");
        failure(() -> rooms.start(room.code(), host), "ROOM_EXPIRED");
        rooms.enforceTimeouts(); assertThat(rooms.state(room.code(), host).status()).isEqualTo("expired");
        assertThat(roundRepository.countByRoomId(room.id())).isZero();
    }
    @Test void audienceAllProfilesAndSettingsRollbackAreRechecked() {
        String host = profile(5, "ru"), guest = profile(4, "ru"), missing = profile(null, "ru");
        String game = tx(() -> {
            var f = factForm(2); f.setAudiencePolicy(AudiencePolicy.ROOM_GRADE); f.setGradeSelectionMode(GradeSelectionMode.PROFILE_DEFAULT);
            f.setGradeIds(grades.findAllByOrderByGradeNoAsc().stream().filter(g -> g.getGradeNo() == 4 || g.getGradeNo() == 5).map(g -> g.getId()).toList());
            return remember(publishGame(f));
        });
        var room = create(host, createRequest(game, "grades"));
        failure(() -> rooms.join(room.code(), guest), "INVALID_GRADE");
        failure(() -> rooms.join(room.code(), missing), "PROFILE_GRADE_REQUIRED");
        Long grade4 = tx(() -> grades.findAllByOrderByGradeNoAsc().stream().filter(g -> g.getGradeNo() == 4).findFirst().orElseThrow().getId());
        Long grade5 = tx(() -> grades.findAllByOrderByGradeNoAsc().stream().filter(g -> g.getGradeNo() == 5).findFirst().orElseThrow().getId());
        var changed = rooms.settings(room.code(), host, new QuizGameRoomRequest.Settings(grade4, null, null, room.settingsRevision()));
        rooms.join(room.code(), guest); rooms.ready(room.code(), guest, new QuizGameRoomRequest.Ready(true, changed.settingsRevision()));
        failure(() -> rooms.settings(room.code(), host, new QuizGameRoomRequest.Settings(grade5, null, null, changed.settingsRevision())), "AUDIENCE_INVALID");
        var unchanged = rooms.state(room.code(), host); assertThat(unchanged.audienceGradeNo()).isEqualTo(4);
        assertThat(unchanged.settingsRevision()).isEqualTo(changed.settingsRevision()); assertThat(unchanged.participants()).allMatch(QuizGameRoomResponse.Member::ready);
        rooms.start(room.code(), host); assertThat(tx(() -> Set.copyOf(roomRepository.findByCodeIgnoreCase(room.code()).orElseThrow().getAudienceGrades()))).containsExactly(4);
    }
    @Test void sourceArchiveAndInsufficientStartLeaveNoSnapshots() {
        var host = profile(5, "ru"); String game = game(4, 2); var room = create(host, createRequest(game, "archive"));
        tx(() -> {
            var g = gameRepository.findByCode(game).orElseThrow(); var config = entityManager.find(QuizFactGameConfig.class, g.getPublishedVersion().getId());
            var statements = entityManager.createQuery("select s from FactStatement s where s.set.id=:id", FactStatement.class).setParameter("id", config.getFactSet().getId()).getResultList();
            facts.archiveStatement(statements.get(0).getId(), admin()); return null;
        });
        failure(() -> rooms.start(room.code(), host), "INSUFFICIENT_PUBLISHED_CONTENT");
        assertThat(rooms.state(room.code(), host).status()).isEqualTo("waiting"); assertThat(roundRepository.countByRoomId(room.id())).isZero();
        tx(() -> { var g = gameRepository.findByCode(game).orElseThrow(); games.archive(g.getId(), revision(g.getId()), admin()); return null; });
        failure(() -> rooms.start(room.code(), host), "GAME_CONFIGURATION_INVALID");
    }
    @Test void httpSafeDtoMembershipCsrfRoleStableErrorsAndFalse() throws Exception {
        var host = profile(5, "ru"); var outsider = profile(4, "ru"); var room = create(host, createRequest(game(4, 2), "http"));
        mvc.perform(get("/api/quiz/rooms/{code}/preview", room.code()).with(user(outsider).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("gameTitle").value("Тестовая игра")).andExpect(jsonPath("participants").doesNotExist());
        mvc.perform(get("/api/quiz/rooms/{code}", room.code()).with(user(outsider).roles("STUDENT")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("PARTICIPANT_REQUIRED"));
        mvc.perform(post("/api/quiz/rooms/{code}/start", room.code()).with(user(host).roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/quiz/rooms/{code}", room.code()).with(user(host).roles("PARENT"))).andExpect(status().isForbidden());
        var active = rooms.start(room.code(), host);
        mvc.perform(get("/api/quiz/rooms/{code}", room.code()).with(user(host).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("currentRound.options[0]").value(true))
                .andExpect(jsonPath("rounds[1].prompt").doesNotExist()).andExpect(jsonPath("currentRound.answerKey").doesNotExist())
                .andExpect(jsonPath("currentRound.isCorrect").doesNotExist()).andExpect(jsonPath("currentRound.explanation").doesNotExist());
        mvc.perform(post("/api/quiz/rooms/{code}/answers", room.code()).with(user(host).roles("STUDENT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"roundId\":" + active.activeRoundId() + ",\"answer\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("currentRound.answered").value(true));
        mvc.perform(get("/api/quiz/rooms/QZ0000").with(user(host).roles("STUDENT")).param("lang", "kk"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("ROOM_CODE_INVALID")).andExpect(jsonPath("message").isNotEmpty());
        mvc.perform(get("/api/quiz/rooms/{code}/results", room.code()).with(user(host).roles("STUDENT")))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("RESULTS_NOT_AVAILABLE"));
        assertThat(mapper.writeValueAsString(rooms.state(room.code(), host))).doesNotContain("answerKey", "isCorrect", "explanation", "sourceReference", "sourceVersionId");
    }
    @Test void createHttpRejectsClientBankMaxPlayersAndMissingKey() throws Exception {
        var host = profile(5, "ru"); var game = game(1, 1);
        for (String body : List.of("{\"gameCode\":\"" + game + "\"}", "{\"gameCode\":\"" + game + "\",\"maxPlayers\":4,\"idempotencyKey\":\"a\"}"))
            mvc.perform(post("/api/quiz/rooms").with(user(host).roles("STUDENT")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("REQUEST_INVALID"));
        String valid = "{\"gameCode\":\"" + game + "\",\"idempotencyKey\":\"http-once\"}";
        String created = mvc.perform(post("/api/quiz/rooms").with(user(host).roles("STUDENT")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isCreated()).andExpect(jsonPath("status").value("waiting")).andExpect(jsonPath("maxPlayers").value(1))
                .andReturn().getResponse().getContentAsString();
        var first = mapper.readTree(created); createdRooms.add(first.path("code").asText());
        mvc.perform(post("/api/quiz/rooms").with(user(host).roles("STUDENT")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isCreated()).andExpect(jsonPath("id").value(first.path("id").longValue()));
    }
    @Test void forcedCollisionRetryAndExhaustionRollbackWholeCreate() {
        var host = profile(5, "ru"); String game = game(4, 1); var first = create(host, createRequest(game, "collision1"));
        doReturn(first.code()).doCallRealMethod().when(generator).generate();
        var second = create(host, createRequest(game, "collision2")); assertThat(second.code()).isNotEqualTo(first.code());
        doReturn(first.code()).when(generator).generate();
        long count = roomRepository.count(); failure(() -> rooms.create(host, createRequest(game, "exhausted")), "ROOM_CODE_EXHAUSTED");
        assertThat(roomRepository.count()).isEqualTo(count);
    }
    @Test void uniqueInsertCollisionRetriesInFreshTransactionWithoutOrphanHostOrKey() throws Exception {
        var game = game(4, 1); String host1 = profile(5, "ru"), host2 = profile(4, "ru");
        String collision = generator.generate(); doReturn(collision).doReturn(collision).doCallRealMethod().when(generator).generate();
        var barrier = new CyclicBarrier(2); var calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            Object value = invocation.callRealMethod();
            // Обе транзакции прошли exists(code)==false и ещё не INSERT: unique violation воспроизводится намеренно.
            if (calls.incrementAndGet() <= 2) barrier.await(10, TimeUnit.SECONDS); return value;
        }).when(factProvider).prepare(any(), any());
        var responses = race(() -> rooms.create(host1, createRequest(game, "unique1")), () -> rooms.create(host2, createRequest(game, "unique2")));
        assertThat(responses).allMatch(v -> v instanceof QuizGameRoomResponse);
        var first = (QuizGameRoomResponse) responses.get(0); var second = (QuizGameRoomResponse) responses.get(1); createdRooms.add(first.code()); createdRooms.add(second.code());
        assertThat(first.code()).isNotEqualTo(second.code()); assertThat(participantRepository.countByRoomId(first.id())).isEqualTo(1); assertThat(participantRepository.countByRoomId(second.id())).isEqualTo(1);
        assertThat(rooms.create(host1, createRequest(game, "unique1")).id()).isEqualTo(first.id());
        assertThat(rooms.create(host2, createRequest(game, "unique2")).id()).isEqualTo(second.id());
    }
    @Test void failureAfterBatchSaveRollsBackStartAndWholeRoundSet() {
        var host = profile(5, "ru"); var room = create(host, createRequest(game(4, 2), "rollback"));
        doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("synthetic failure after batch INSERT"); }).when(factory).create(room.code());
        assertThatThrownBy(() -> rooms.start(room.code(), host)).isInstanceOf(IllegalStateException.class).hasMessageContaining("synthetic failure");
        assertThat(roundRepository.countByRoomId(room.id())).isZero(); assertThat(rooms.state(room.code(), host).status()).isEqualTo("waiting");
        reset(factory);
        doAnswer(invocation -> { Object value = invocation.callRealMethod(); clock.set(BASE.plusSeconds(1800)); return value; }).when(factory).create(room.code());
        failure(() -> rooms.start(room.code(), host), "ROOM_EXPIRED");
        assertThat(roundRepository.countByRoomId(room.id())).isZero(); assertThat(rooms.state(room.code(), host).status()).isEqualTo("expired");
    }
    @Test void expireAndStartRaceAtBoundaryNeverStartsExpiredRoom() throws Exception {
        var host = profile(5, "ru"); var room = create(host, createRequest(game(1, 1), "expirerace")); clock.set(BASE.plusSeconds(1800));
        var values = race(() -> rooms.start(room.code(), host), () -> { rooms.tick(room.code()); return "expired"; });
        assertThat(values).containsExactlyInAnyOrder("ROOM_EXPIRED", "expired"); assertThat(roundRepository.countByRoomId(room.id())).isZero(); assertThat(rooms.state(room.code(), host).status()).isEqualTo("expired");
    }
    @Test void httpCommandsHonorRevisionStrictPayloadAndSameOrigin() throws Exception {
        String host = profile(5, "ru"), guest = profile(4, "ru"); var room = create(host, createRequest(game(4, 1), "commands"));
        mvc.perform(post("/api/quiz/rooms/{code}/join", room.code()).with(user(guest).roles("STUDENT")).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/quiz/rooms/{code}/ready", room.code()).with(user(guest).roles("STUDENT")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ready\":true,\"settingsRevision\":" + room.settingsRevision() + "}")).andExpect(status().isOk());
        mvc.perform(post("/api/quiz/rooms/{code}/settings", room.code()).with(user(host).roles("STUDENT")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"questionCount\":1,\"roundSeconds\":10,\"settingsRevision\":" + room.settingsRevision() + "}")).andExpect(status().isOk()).andExpect(jsonPath("settingsRevision").value(room.settingsRevision() + 1));
        mvc.perform(post("/api/quiz/rooms/{code}/ready", room.code()).with(user(guest).roles("STUDENT")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ready\":true,\"settingsRevision\":" + room.settingsRevision() + "}")).andExpect(status().isConflict()).andExpect(jsonPath("code").value("SETTINGS_REVISION_CONFLICT"));
        mvc.perform(post("/api/quiz/rooms/{code}/settings", room.code()).with(user(host).roles("STUDENT")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"questionCount\":\"1\",\"settingsRevision\":1}")).andExpect(status().isBadRequest());
        mvc.perform(get("/ws/quiz").with(user(host).roles("STUDENT")).header("Origin", "https://untrusted.invalid").header("Upgrade", "websocket")
                .header("Connection", "Upgrade").header("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==").header("Sec-WebSocket-Version", "13")).andExpect(status().isForbidden());
        mvc.perform(post("/api/quiz/rooms/{code}/leave", room.code()).with(user(guest).roles("STUDENT")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post("/api/quiz/rooms/{code}/cancel", room.code()).with(user(host).roles("STUDENT")).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("status").value("cancelled"));
    }
    @Test void concurrentCreateStartAndLastSlotAreSerialized() throws Exception {
        String host = profile(5, "ru"), guest1 = profile(4, "ru"), guest2 = profile(5, "ru"), game = game(2, 1);
        var request = createRequest(game, "concurrent");
        var creates = race(() -> rooms.create(host, request), () -> rooms.create(host, request));
        var first = (QuizGameRoomResponse) creates.get(0); createdRooms.add(first.code());
        assertThat(((QuizGameRoomResponse) creates.get(1)).id()).isEqualTo(first.id());
        var joins = race(() -> rooms.join(first.code(), guest1), () -> rooms.join(first.code(), guest2));
        assertThat(joins.stream().filter(v -> v instanceof QuizGameRoomResponse).count()).isEqualTo(1);
        assertThat(joins.stream().filter(v -> v instanceof String)).containsExactly("ROOM_FULL");
        String guest = joins.get(0) instanceof QuizGameRoomResponse ? guest1 : guest2;
        rooms.ready(first.code(), guest, new QuizGameRoomRequest.Ready(true, first.settingsRevision()));
        var starts = race(() -> rooms.start(first.code(), host), () -> rooms.start(first.code(), host));
        assertThat(starts).allMatch(v -> v instanceof QuizGameRoomResponse r && r.status().equals("active"));
        assertThat(roundRepository.countByRoomId(first.id())).isEqualTo(1); assertThat(participantRepository.countByRoomId(first.id())).isEqualTo(2);
    }
    @Test void concurrentDifferentAnswerCannotOverwriteAndTimeoutCannotReplace() throws Exception {
        var host = profile(5, "ru"); var room = create(host, createRequest(game(1, 1), "answers")); Long id = rooms.start(room.code(), host).activeRoundId();
        var submissions = race(() -> rooms.answer(room.code(), host, new QuizGameRoomRequest.Answer(id, mapper.getNodeFactory().booleanNode(false), false)),
                () -> rooms.answer(room.code(), host, new QuizGameRoomRequest.Answer(id, mapper.getNodeFactory().booleanNode(true), false)));
        assertThat(submissions.stream().filter(v -> v instanceof String)).containsExactly("ANSWER_ALREADY_SUBMITTED");
        var before = tx(() -> answerRepository.findByRoundRoomId(room.id()).get(0).getAnswerJson());
        clock.set(BASE.plusSeconds(20)); race(() -> { rooms.tick(room.code()); return "done"; }, () -> { rooms.tick(room.code()); return "done"; });
        assertThat(tx(() -> answerRepository.findByRoundRoomId(room.id()).get(0).getAnswerJson())).isEqualTo(before);
        assertThat(resultRepository.findByRoomIdOrderByScoreDescPercentDescCreatedAtAsc(room.id())).hasSize(1);
    }
    @Test void concurrentStartJoinCancelSettingsAndReadyPreserveInvariants() throws Exception {
        var host = profile(5, "ru"); var guest = profile(4, "ru");
        var room = create(host, createRequest(game(4, 2), "startjoin"));
        race(() -> rooms.start(room.code(), host), () -> rooms.join(room.code(), guest));
        var state = rooms.state(room.code(), host);
        if (state.status().equals("active")) assertThat(state.participants()).hasSize(1);
        else { assertThat(state.participants()).hasSize(2); assertThat(roundRepository.countByRoomId(room.id())).isZero(); }
        var cancelRoom = create(host, createRequest(game(4, 1), "cancelstart"));
        race(() -> rooms.start(cancelRoom.code(), host), () -> rooms.cancel(cancelRoom.code(), host));
        var after = rooms.state(cancelRoom.code(), host);
        assertThat(after.status()).isIn("active", "cancelled"); assertThat(roundRepository.countByRoomId(after.id())).isEqualTo(after.status().equals("active") ? 1 : 0);
        var settingsRoom = create(host, createRequest(game(4, 2), "settingsready")); rooms.join(settingsRoom.code(), guest);
        race(() -> rooms.settings(settingsRoom.code(), host, new QuizGameRoomRequest.Settings(null, 1, 10, settingsRoom.settingsRevision())),
                () -> rooms.ready(settingsRoom.code(), guest, new QuizGameRoomRequest.Ready(true, settingsRoom.settingsRevision())));
        var changed = rooms.state(settingsRoom.code(), host); assertThat(changed.participants().stream().filter(p -> !p.host())).allMatch(p -> !p.ready());
        rooms.ready(changed.code(), guest, new QuizGameRoomRequest.Ready(true, changed.settingsRevision()));
        race(() -> rooms.start(changed.code(), host), () -> rooms.settings(changed.code(), host, new QuizGameRoomRequest.Settings(null, 2, 20, changed.settingsRevision())));
        var finalState = rooms.state(changed.code(), host);
        if (finalState.status().equals("active")) assertThat(roundRepository.countByRoomId(finalState.id())).isEqualTo(finalState.questionCount());
        else assertThat(finalState.participants().stream().filter(p -> !p.host())).allMatch(p -> !p.ready());
    }
    @Test void answerAtRightBoundaryAndFutureRoundAreRejected() {
        var host = profile(5, "ru"); var room = create(host, createRequest(game(1, 2), "boundary")); var active = rooms.start(room.code(), host);
        Long future = tx(() -> roundRepository.findByRoomIdOrderByOrderNoAsc(room.id()).get(1).getId());
        failure(() -> rooms.answer(room.code(), host, new QuizGameRoomRequest.Answer(future, mapper.getNodeFactory().booleanNode(false), false)), "ROUND_NOT_ACTIVE");
        clock.set(BASE.plusSeconds(20));
        raceUnchecked(() -> rooms.answer(room.code(), host, new QuizGameRoomRequest.Answer(active.activeRoundId(), mapper.getNodeFactory().booleanNode(false), false)), () -> { rooms.tick(room.code()); return "tick"; });
        assertThat(tx(() -> answerRepository.findByRoundRoomId(room.id()).get(0).isTimedOut())).isTrue();
    }
    @Test void stompRoleExactTopicMultipleTabsLeaveReconnectAndQueuedDelivery() {
        var host = profile(5, "ru"); var guest = profile(4, "ru"); var outsider = profile(4, "ru"); var room = create(host, createRequest(game(4, 1), "stomp")); rooms.join(room.code(), guest);
        assertThatThrownBy(() -> subscriptions.inbound(subscribe(room.code(), outsider, "STUDENT", "x", "1"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> subscriptions.inbound(subscribe(room.code(), guest, "PARENT", "x", "1"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> subscriptions.inbound(subscribe(room.code(), guest, "ADMIN", "x", "1"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        var anonymous = StompHeaderAccessor.create(StompCommand.CONNECT);
        assertThatThrownBy(() -> subscriptions.inbound(MessageBuilder.createMessage(new byte[0], anonymous.getMessageHeaders()))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> subscriptions.inbound(subscribe("*", guest, "STUDENT", "x", "1"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        var forged = StompHeaderAccessor.create(StompCommand.SEND); forged.setUser(auth(guest, "STUDENT")); forged.setDestination("/topic/quiz.rooms." + room.code());
        assertThatThrownBy(() -> subscriptions.inbound(MessageBuilder.createMessage(new byte[0], forged.getMessageHeaders()))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        subscriptions.inbound(subscribe(room.code(), guest, "STUDENT", "tab1", "1")); subscriptions.inbound(subscribe(room.code(), guest, "STUDENT", "tab2", "2"));
        var delivered = outbound(room.code(), "tab1", "1"); assertThat(subscriptions.beforeHandle(delivered, null, null)).isNotNull(); subscriptions.afterMessageHandled(delivered, null, null, null);
        rooms.leave(room.code(), guest);
        assertThat(subscriptions.beforeHandle(delivered, null, null)).isNull(); assertThat(subscriptions.beforeHandle(outbound(room.code(), "tab2", "2"), null, null)).isNull();
        assertThatThrownBy(() -> subscriptions.inbound(subscribe(room.code(), guest, "STUDENT", "reconnect", "1"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        rooms.join(room.code(), guest); subscriptions.inbound(subscribe(room.code(), guest, "STUDENT", "reconnect", "1"));
        var rejoined = outbound(room.code(), "reconnect", "1"); assertThat(subscriptions.beforeHandle(rejoined, null, null)).isNotNull(); subscriptions.afterMessageHandled(rejoined, null, null, null);
    }
    @Test void realBrokerChannelsApplyDeliveryGuardAndSafeEvents() throws Exception {
        String host = profile(5, "ru"), guest = profile(4, "ru"); var room = create(host, createRequest(game(4, 1), "broker")); rooms.join(room.code(), guest);
        var received = new LinkedBlockingQueue<Message<?>>();
        MessageHandler handler = message -> { var h = SimpMessageHeaderAccessor.wrap(message); if ("broker-tab".equals(h.getSessionId()) && h.getMessageType() == SimpMessageType.MESSAGE) received.add(message); };
        outbound.subscribe(handler);
        try {
            var connect = StompHeaderAccessor.create(StompCommand.CONNECT); connect.setUser(auth(guest, "STUDENT")); connect.setSessionId("broker-tab"); connect.setSessionAttributes(new HashMap<>());
            inbound.send(MessageBuilder.createMessage(new byte[0], connect.getMessageHeaders()));
            inbound.send(subscribe(room.code(), guest, "STUDENT", "broker-tab", "broker-sub"));
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> broker.getSubscriptionRegistry().findSubscriptions(outbound(room.code(), "broker-tab", "broker-sub")).containsKey("broker-tab"));
            tx(() -> { publisher.publish("room.readiness_changed", roomRepository.findByCodeIgnoreCase(room.code()).orElseThrow()); return null; });
            var event = received.poll(5, TimeUnit.SECONDS); assertThat(event).isNotNull();
            assertThat(mapper.readTree((byte[]) event.getPayload()).fieldNames()).toIterable().containsExactlyInAnyOrder("type", "code", "status", "occurredAt");
            rooms.leave(room.code(), guest);
            tx(() -> { publisher.publish("room.settings_changed", roomRepository.findByCodeIgnoreCase(room.code()).orElseThrow()); return null; });
            assertThat(received.poll(200, TimeUnit.MILLISECONDS)).isNull();
        } finally { subscriptions.removeSession("broker-tab"); outbound.unsubscribe(handler); }
    }
    @Test void leaveWaitsForDeliveryThenRevokesBeforeSubsequentEvents() throws Exception {
        var host = profile(5, "ru"); var guest = profile(4, "ru"); var room = create(host, createRequest(game(4, 1), "deliveryrace")); rooms.join(room.code(), guest);
        subscriptions.inbound(subscribe(room.code(), guest, "STUDENT", "race", "1")); var message = outbound(room.code(), "race", "1");
        assertThat(subscriptions.beforeHandle(message, null, null)).isNotNull();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var started = new CountDownLatch(1); var left = executor.submit(() -> { started.countDown(); rooms.leave(room.code(), guest); });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue(); assertThat(left.isDone()).isFalse();
            subscriptions.afterMessageHandled(message, null, null, null); left.get(10, TimeUnit.SECONDS);
            assertThat(subscriptions.beforeHandle(message, null, null)).isNull();
        }
    }
    private String profile(Integer grade, String language) {
        return tx(() -> { var email = UUID.randomUUID() + "@room.test"; var user = users.save(new AppUser(email, "test", "Участник", null)); profiles.save(new StudentProfile(user, grade, language)); return email; });
    }
    private String game(int maxPlayers, int count) {
        return tx(() -> { var f = factForm(count); f.setMaxPlayers(maxPlayers); f.setDefaultQuestionCount(count); f.setMaxQuestionCount(count);
            f.setQuestionCountEditable(true); f.setRoundSecondsEditable(true); return remember(publishGame(f)); });
    }
    private String remember(QuizGame game) { createdGames.add(game.getId()); return game.getCode(); }
    private QuizGameRoomRequest.Create createRequest(String game, String key) { return new QuizGameRoomRequest.Create(game, null, null, null, key); }
    private QuizGameRoomResponse create(String host, QuizGameRoomRequest.Create request) { var room = rooms.create(host, request); createdRooms.add(room.code()); return room; }
    private <T> T tx(Supplier<T> action) { return new TransactionTemplate(manager).execute(status -> action.get()); }
    private void failure(Runnable action, String code) { assertThatThrownBy(action::run).isInstanceOf(QuizEngineException.class).hasMessage(code); }
    private List<Object> race(Supplier<?> a, Supplier<?> b) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            java.util.function.Function<Supplier<?>, Callable<Object>> task = action -> () -> {
                barrier.await(10, TimeUnit.SECONDS); try { return action.get(); } catch (QuizEngineException ex) { return ex.getCode(); }
            };
            var first = executor.submit(task.apply(a)); var second = executor.submit(task.apply(b)); return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }
    }
    private void raceUnchecked(Supplier<?> a, Supplier<?> b) { try { race(a, b); } catch (Exception ex) { throw new AssertionError(ex); } }
    private UsernamePasswordAuthenticationToken auth(String email, String role) { return new UsernamePasswordAuthenticationToken(email, "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role))); }
    private Message<?> subscribe(String code, String email, String role, String session, String id) {
        var h = StompHeaderAccessor.create(StompCommand.SUBSCRIBE); h.setUser(auth(email, role)); h.setSessionId(session); h.setSubscriptionId(id); h.setDestination("/topic/quiz.rooms." + code); return MessageBuilder.createMessage(new byte[0], h.getMessageHeaders());
    }
    private Message<?> outbound(String code, String session, String id) {
        var h = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE); h.setSessionId(session); h.setSubscriptionId(id); h.setDestination("/topic/quiz.rooms." + code); return MessageBuilder.createMessage(new byte[0], h.getMessageHeaders());
    }
    /** Управляемые серверные часы проверяют границы без ожидания реальных таймеров. */
    static class MutableClock extends Clock {
        private volatile Instant instant = BASE;
        void set(Instant value) { instant = value; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary MutableClock quizRoomClock() { return new MutableClock(); }
    }
}
