package kz.damulab.quiz.catalog;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import kz.damulab.quiz.*;
import kz.damulab.quiz.engine.*;
import kz.damulab.questions.*;
import kz.damulab.users.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Приёмка движка на реальной схеме H2/PostgreSQL: снимки, аудитория, безопасная выдача и атомарность. */
@Transactional
class QuizEngineIntegrationTest extends QuizGameTestSupport {
    @Autowired QuizRoundFactory factory;
    @Autowired FactRoundProvider factProvider;
    @Autowired VocabularyRoundProvider wordProvider;
    @Autowired SubjectRoundProvider subjectProvider;
    @Autowired QuizRoundAnswerChecker checker;
    @Autowired QuizAnswerEvaluator evaluator;
    @Autowired QuizCurrentRoundReader reader;
    @Autowired QuizGameResultService resultService;
    @Autowired QuizRoundRepository roundRepository;
    @Autowired QuizRoomRepository roomRepository;
    @Autowired QuizParticipantRepository participantRepository;
    @Autowired QuizAnswerRepository answerRepository;
    @Autowired QuizResultRepository resultRepository;
    @Autowired StudentProfileRepository profiles;
    @Autowired ObjectMapper mapper;
    @Autowired QuizSnapshotJson json;
    @Autowired VocabularyDistractorService distractors;
    @Autowired PlatformTransactionManager transactionManager;
    private final OffsetDateTime start = OffsetDateTime.parse("2026-10-10T10:00:00Z");

    @AfterEach void closeCommittedSyntheticFixtures() {
        var tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
            // Тесты реального rollback обязаны сначала commit фикстуру. После проверки сохраняем её историю,
            // но убираем из живого каталога/expiry-выборки; домен email принадлежит только этому набору тестов.
            var committed = entityManager.createQuery("select r from QuizRoom r join r.hostStudentProfile p join p.user u where u.email like :email", QuizRoom.class)
                    .setParameter("email", "%@engine.test").getResultList();
            for (var room : committed) {
                if (room.getStatus() == QuizRoomStatus.WAITING) room.cancel(OffsetDateTime.now());
                var game = room.getGame();
                if (game != null && game.getStatus() != EditorialStatus.ARCHIVED) games.archive(game.getId(), revision(game.getId()), admin());
            }
        });
    }

    @Test void fullWordSetIsUniqueShuffledOnceAndSharedByParticipants() throws Exception {
        var f = form(QuizGameType.WORD_TRANSLATION); var set = wordSet(5); f.setVocabularySetId(set.getId()); f.setSourceLanguage("en"); f.setTargetLanguage("ru"); counts(f, 5);
        var room = room(publishGame(f), 5, "ru", 5, 4); var rounds = factory.create(room.getCode());
        assertThat(rounds).hasSize(5); assertThat(rounds).extracting(r -> r.getVocabularyItemVersion().getId()).doesNotHaveDuplicates();
        assertThat(rounds).extracting(QuizRound::getOrderNo).containsExactly(1, 2, 3, 4, 5);
        for (var r : rounds) {
            var options = json.read(r.getOptionsSnapshotJson()); assertThat(options.size()).isEqualTo(4);
            var selected = json.read("{\"selected\":[" + r.getAnswerKeySnapshotJson() + "]}"); assertThat(checker.check(r, selected).correct()).isTrue();
            assertThat(r.getQuestionVersion()).isNull(); assertThat(r.getVocabularySetVersion().getId()).isEqualTo(set.getPublishedVersion().getId());
            assertThat(options.findValuesAsText("text")).doesNotHaveDuplicates();
        }
        room.start(start); var participants = participantRepository.findByRoomIdOrderByJoinedAtAscIdAsc(room.getId());
        var a = reader.read(room.getCode(), participants.get(0).getStudentProfile().getId(), start.plusSeconds(1));
        var b = reader.read(room.getCode(), participants.get(1).getStudentProfile().getId(), start.plusSeconds(1));
        assertThat(a.currentRound()).isEqualTo(b.currentRound());
        entityManager.flush(); entityManager.clear();
        assertThat(roundRepository.findById(rounds.getFirst().getId()).orElseThrow().getOptionsSnapshotJson()).isEqualTo(rounds.getFirst().getOptionsSnapshotJson());
        assertThatThrownBy(() -> factory.create(room.getCode())).hasMessage("ROUND_SET_ALREADY_CREATED");
    }
    @ParameterizedTest @EnumSource(QuestionType.class)
    void allSubjectFormatsBuildSafeSnapshotAndCheckAfterBankChanges(QuestionType type) {
        var grade = grades.findAllByOrderByGradeNoAsc().stream().filter(g -> g.getGradeNo() == 5).findFirst().orElseThrow();
        var subject = subjects.findAll().getFirst(); var source = subjectQuestion(subject, type, 2, List.of(grade.getId()));
        source.replaceContent(validSubject(source, type));
        var f = form(QuizGameType.SUBJECT_QUIZ); f.setSubjectId(subject.getId()); f.setQuestionTypes(Set.of(type)); f.setDifficulty(2);
        f.setAudiencePolicy(AudiencePolicy.ROOM_GRADE); f.setGradeSelectionMode(GradeSelectionMode.REQUIRED); f.setGradeIds(List.of(grade.getId()));
        var room = room(publishGame(f), 1, "kk", 5); room.changeSettings(5, Set.of(), 1, room.getRoundSeconds());
        var round = factory.create(room.getCode()).getFirst(); String saved = round.getOptionsSnapshotJson();
        assertThat(round.getPromptSnapshot()).isEqualTo("Сұрақ");
        assertThat(saved).doesNotContain("isCorrect", "answerKey", "SECRET", "mode", "tolerance");
        if (type == QuestionType.MATCHING) { assertThat(json.read(saved).has("left")).isTrue(); assertThat(json.read(saved).has("right")).isTrue(); assertThat(json.read(saved).has("pairs")).isFalse(); }
        if (type == QuestionType.FILL_IN) assertThat(json.read(saved).path("placeholders").get(0).asText()).isEqualTo("[[1]]");
        var good = correctSubjectAnswer(type); assertThat(checker.check(round, good).correct()).isTrue();
        source.replaceContent(new QuestionVersion(source.getQuestion(), 1, QuestionType.SCQ, subject, null, 5, "Изменён", "Өзгерді", "Новый разбор", "Жаңа", null, null, "Новый источник", "[]", "[\"Z\"]"));
        source.getQuestion().changeStatus(QuestionStatus.ARCHIVED); entityManager.flush(); entityManager.clear();
        var restored = roundRepository.findById(round.getId()).orElseThrow();
        assertThat(restored.getOptionsSnapshotJson()).isEqualTo(saved); assertThat(checker.check(restored, good).correct()).isTrue();
    }
    @Test void audienceIsIntersectionOfAllActualGradesAndNotUnion() {
        var set = factSet(2); var g5 = gradeId(5); var g6 = gradeId(4);
        publishFact(set, false, List.of(g5)); publishFact(set, false, List.of(g6)); var both = publishFact(set, false, List.of(g5, g6));
        var f = form(QuizGameType.TRUE_FALSE); f.setFactSetId(set.getId()); f.setAudiencePolicy(AudiencePolicy.PARTICIPANT_GRADES); counts(f, 2);
        var room = room(publishGame(f), 2, "ru", 5, 4);
        var prepared = factProvider.prepare(room, Set.of(g5, g6));
        assertThat(prepared).hasSize(3); assertThat(prepared).extracting(QuizRoundProvider.Source::versionId).contains(both.getPublishedVersion().getId());
        factory.create(room.getCode()); assertThat(room.getAudienceGrades()).containsExactlyInAnyOrder(5, 4);
    }
    @Test void universalIsExplicitAndEmptyGradesAreNotUniversal() {
        var set = factSet(1); var specific = publishFact(set, false, List.of(gradeId(5)));
        var f = form(QuizGameType.TRUE_FALSE); f.setFactSetId(set.getId()); var room = room(publishGame(f), 1, "ru", 5);
        assertThat(factProvider.prepare(room, Set.of())).hasSize(1);
        specific.getPublishedVersion().setGradeIds(Set.of());
        assertThat(factProvider.prepare(room, Set.of())).hasSize(1);
        assertThatThrownBy(() -> subjectProvider.prepare(room, Set.of())).hasMessage("SUBJECT_UNIVERSAL_UNSUPPORTED");
    }
    @Test void missingActualGradeBlocksEvenIfSettingsHaveOldAudience() {
        var f = factForm(1); f.setAudiencePolicy(AudiencePolicy.PARTICIPANT_GRADES); var room = room(publishGame(f), 1, "ru", 5, 4);
        var guest = participantRepository.findByRoomIdOrderByJoinedAtAscIdAsc(room.getId()).getLast().getStudentProfile(); guest.update(null, "ru");
        room.changeSettings(null, Set.of(5), 1, room.getRoundSeconds());
        assertThatThrownBy(() -> factory.create(room.getCode())).hasMessage("PROFILE_GRADE_REQUIRED");
        assertThat(roundRepository.countByRoomId(room.getId())).isZero();
    }
    @Test void fixedAndSelectedContentGradesDoNotRestrictParticipantProfiles() {
        var f = factForm(1); f.setAudiencePolicy(AudiencePolicy.FIXED_GRADES); f.setGradeIds(List.of(gradeId(5)));
        var room = room(publishGame(f), 1, "ru", 5, 4);
        var guest = participantRepository.findByRoomIdOrderByJoinedAtAscIdAsc(room.getId()).getLast().getStudentProfile();
        guest.update(null, "ru");
        assertThat(factory.create(room.getCode())).hasSize(1);
        assertThat(room.getAudienceGrades()).containsExactly(5);
        var rf = factForm(1); rf.setAudiencePolicy(AudiencePolicy.ROOM_GRADE); rf.setGradeSelectionMode(GradeSelectionMode.REQUIRED); rf.setGradeIds(List.of(gradeId(5)));
        var other = room(publishGame(rf), 1, "ru", 4); other.changeSettings(5, Set.of(), 1, other.getRoundSeconds());
        assertThat(factory.create(other.getCode())).hasSize(1);
        assertThat(other.getAudienceGrades()).containsExactly(5);
    }
    @Test void currentPublicationArchiveAndDraftAreRespectedAtFormation() {
        var set = factSet(2); var f = form(QuizGameType.TRUE_FALSE); f.setFactSetId(set.getId()); counts(f, 2); var game = publishGame(f);
        var room = room(game, 2, "ru", 5);
        var source = factProvider.prepare(room, Set.of()).getFirst().builder().build(1, new Random(1)).getFactStatementVersion().getStatement();
        var replacement = factEdit(source, true); facts.saveDraft(source.getId(), replacement, admin());
        assertThat(factProvider.prepare(room, Set.of())).extracting(QuizRoundProvider.Source::versionId).contains(source.getPublishedVersion().getId());
        facts.submit(source.getId(), admin()); facts.approve(source.getId(), admin()); facts.publish(source.getId(), admin());
        assertThat(factProvider.prepare(room, Set.of())).extracting(QuizRoundProvider.Source::versionId).contains(source.getPublishedVersion().getId());
        facts.archiveStatement(source.getId(), admin());
        assertThatThrownBy(() -> factory.create(room.getCode())).isInstanceOfSatisfying(QuizEngineException.class, e -> { assertThat(e.getAvailable()).isEqualTo(1); assertThat(e.getRequired()).isEqualTo(2); });
        assertThat(roundRepository.countByRoomId(room.getId())).isZero(); assertThat(room.getStatus()).isEqualTo(QuizRoomStatus.WAITING);
    }
    @Test void archivedSetBlocksStartWithoutPartialSnapshots() {
        var f = factForm(1); var room = room(publishGame(f), 1, "ru", 5); facts.archiveSet(f.getFactSetId(), admin());
        assertThatThrownBy(() -> factory.create(room.getCode())).isInstanceOf(ContentValidationException.class);
        assertThat(roundRepository.countByRoomId(room.getId())).isZero();
    }
    @Test void missingDistractorsDoesNotInvalidateBankPublicationButPreventsGame() {
        var set = wordSet(4); var f = form(QuizGameType.WORD_TRANSLATION); f.setVocabularySetId(set.getId()); f.setSourceLanguage("en"); f.setTargetLanguage("ru");
        var room = room(publishGame(f), 1, "ru", 5);
        var sources = wordProvider.prepare(room, Set.of());
        var source = sources.getFirst().builder().build(1, new Random()).getVocabularyItemVersion();
        var other = sources.getLast().builder().build(1, new Random()).getVocabularyItemVersion();
        distractors.exclude(source.getId(), other.getId(), "Синонимы", admin());
        assertThat(wordProvider.prepare(room, Set.of())).hasSize(2);
        vocabulary.archiveItem(other.getItem().getId(), admin());
        assertThat(source.getStatus()).isEqualTo(EditorialStatus.PUBLISHED);
        assertThatThrownBy(() -> factory.create(room.getCode())).hasMessage("INSUFFICIENT_PUBLISHED_CONTENT");
        assertThat(roundRepository.countByRoomId(room.getId())).isZero();
    }
    @Test void safeCurrentDtoHasNoKeysFutureTextExplanationOrCorrectness() throws Exception {
        var f = factForm(2); counts(f, 2); var room = room(publishGame(f), 2, "ru", 5, 4);
        var sourceBuilders = factProvider.prepare(room, Set.of());
        for (int i = 0; i < sourceBuilders.size(); i++) sourceBuilders.get(i).builder().build(1, new Random()).getFactStatementVersion().setTextRu("Уникальное условие " + i + " " + UUID.randomUUID());
        var snapshots = factory.create(room.getCode());
        room.start(start); var ps = participantRepository.findByRoomIdOrderByJoinedAtAscIdAsc(room.getId()); var current = snapshots.getFirst();
        answerRepository.save(evaluator.evaluate(current, ps.getFirst(), json.read("false"), false, start.plusSeconds(1)));
        var state = reader.read(room.getCode(), ps.getFirst().getStudentProfile().getId(), start.plusSeconds(2)); String serialized = mapper.writeValueAsString(state);
        assertThat(state.currentRound().id()).isEqualTo(current.getId()); assertThat(state.currentRound().answered()).isTrue();
        assertThat(serialized).doesNotContain("answerKey", "correct", "explanation", "sourceReference", "Научный источник", "Объяснение", "future", "rounds");
        assertThat(serialized).doesNotContain(snapshots.getLast().getPromptSnapshot());
        assertThat(mapper.readTree(serialized).path("currentRound").fieldNames()).toIterable().containsExactlyInAnyOrder("id", "orderNo", "format", "contentKind", "prompt", "options", "locale", "presentation", "startsAt", "endsAt", "answered");
        assertThat(reader.read(room.getCode(), ps.getFirst().getStudentProfile().getId(), start.plusSeconds(room.getRoundSeconds())).currentRound().id()).isEqualTo(snapshots.getLast().getId());
        assertThat(reader.read(room.getCode(), ps.getFirst().getStudentProfile().getId(), start.minusSeconds(1)).currentRound()).isNull();
        assertThatThrownBy(() -> reader.read(room.getCode(), -1L, start)).hasMessage("PARTICIPANT_REQUIRED");
        assertThatThrownBy(() -> resultService.read(room.getCode(), ps.getFirst().getStudentProfile().getId())).hasMessage("RESULTS_NOT_AVAILABLE");
    }
    @Test void factHistoryAndPersonalResultSurviveNewPublicationAndArchive() throws Exception {
        var room = room(publishGame(factForm(1)), 1, "ru", 5, 4); var round = factory.create(room.getCode()).getFirst();
        room.start(start); var ps = participantRepository.findByRoomIdOrderByJoinedAtAscIdAsc(room.getId());
        answerRepository.save(evaluator.evaluate(round, ps.getFirst(), json.read("false"), false, start.plusSeconds(2)));
        answerRepository.save(evaluator.evaluate(round, ps.getLast(), json.read("true"), false, start.plusSeconds(1)));
        room.finish(start.plusSeconds(room.getRoundSeconds())); resultService.save(room.getCode()); resultService.save(room.getCode());
        var before = resultService.read(room.getCode(), ps.getFirst().getStudentProfile().getId());
        assertThat(before.me().score()).isEqualByComparingTo(BigDecimal.ONE); assertThat(before.me().percent()).isEqualTo(100); assertThat(before.me().place()).isEqualTo(1);
        assertThat(before.review()).hasSize(1); assertThat(before.review().getFirst().answer().booleanValue()).isFalse();
        var source = round.getFactStatementVersion().getStatement(); facts.saveDraft(source.getId(), factEdit(source, true), admin());
        facts.submit(source.getId(), admin()); facts.approve(source.getId(), admin()); facts.publish(source.getId(), admin()); facts.archiveStatement(source.getId(), admin()); facts.archiveSet(source.getSet().getId(), admin());
        String expected = mapper.writeValueAsString(before); Long profileId = ps.getFirst().getStudentProfile().getId();
        entityManager.flush(); entityManager.clear();
        assertThat(mapper.writeValueAsString(resultService.read(room.getCode(), profileId))).isEqualTo(expected);
        assertThat(resultRepository.findByRoomIdOrderByScoreDescPercentDescCreatedAtAsc(room.getId())).hasSize(2);
        assertThatThrownBy(() -> resultService.read(room.getCode(), -1L)).hasMessage("PARTICIPANT_REQUIRED");
        assertThat(reader.read(room.getCode(), profileId, start.plusMinutes(1)).currentRound()).isNull();
    }
    @Test void vocabularySnapshotDoesNotFollowItemOrSetRepublication() {
        var set = wordSet(4); var f = form(QuizGameType.WORD_TRANSLATION); f.setVocabularySetId(set.getId()); f.setSourceLanguage("en"); f.setTargetLanguage("ru");
        var room = room(publishGame(f), 1, "ru", 5); var round = factory.create(room.getCode()).getFirst();
        var item = round.getVocabularyItemVersion().getItem(); String options = round.getOptionsSnapshotJson(); String prompt = round.getPromptSnapshot();
        var edit = new VocabularyItemForm(); edit.setSetId(set.getId()); edit.setSourceText("new-word"); edit.setPrimaryAnswer("новый перевод"); edit.setMeaningNoteRu("Новый разбор"); edit.setSourceReference("Новый словарь"); edit.setUniversalAudience(true); edit.setDifficulty(1);
        vocabulary.saveItemDraft(item.getId(), edit, admin()); vocabulary.submitItem(item.getId(), admin()); vocabulary.approveItem(item.getId(), admin()); vocabulary.publishItem(item.getId(), admin()); vocabulary.archiveItem(item.getId(), admin());
        var setEdit = new VocabularySetForm(); setEdit.setCode(set.getCode()); setEdit.setTitleRu("Новый набор"); setEdit.setTitleKk("Жаңа"); setEdit.setSourceLanguage("en"); setEdit.setTargetLanguage("ru"); setEdit.setSourceReference("Новый источник"); setEdit.setCrossSetDistractorsAllowed(true);
        vocabulary.saveSetDraft(set.getId(), setEdit, admin()); vocabulary.submitSet(set.getId(), admin()); vocabulary.approveSet(set.getId(), admin()); vocabulary.publishSet(set.getId(), admin()); vocabulary.archiveSet(set.getId(), admin());
        entityManager.flush(); entityManager.clear(); var restored = roundRepository.findById(round.getId()).orElseThrow();
        assertThat(restored.getPromptSnapshot()).isEqualTo(prompt); assertThat(restored.getOptionsSnapshotJson()).isEqualTo(options);
        assertThat(checker.check(restored, json.read("{\"selected\":[" + restored.getAnswerKeySnapshotJson() + "]}")).correct()).isTrue();
    }
    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void malformedSourceRollsBackWholeFactoryTransaction() {
        var tx = new TransactionTemplate(transactionManager);
        String code = tx.execute(status -> {
            var grade = gradeId(5);
            // Свой предмет исключает влияние ранее сохранённых синтетических фикстур при повторном PostgreSQL-прогоне.
            var subject = subjects.save(new kz.damulab.content.Subject(code(), "Предмет движка", "Қозғалтқыш пәні", "Описание", "Сипаттама"));
            for (int i = 0; i < 2; i++) { var v = subjectQuestion(subject, QuestionType.SCQ, 5, List.of(grade)); v.replaceContent(validSubject(v, QuestionType.SCQ)); }
            var f = form(QuizGameType.SUBJECT_QUIZ); f.setSubjectId(subject.getId()); f.setDifficulty(5); f.setQuestionTypes(Set.of(QuestionType.SCQ)); f.setAudiencePolicy(AudiencePolicy.ROOM_GRADE); f.setGradeSelectionMode(GradeSelectionMode.REQUIRED); f.setGradeIds(List.of(grade)); counts(f, 2);
            var room = room(publishGame(f), 2, "ru", 5); room.changeSettings(5, Set.of(), 2, room.getRoundSeconds());
            var sources = subjectProvider.prepare(room, Set.of(grade));
            // Неисправная текущая публикация возникла после admin-checklist: движок обязан перепроверить формат.
            var invalid = questionVersions.findById(sources.getFirst().versionId()).orElseThrow();
            invalid.replaceContent(new QuestionVersion(invalid.getQuestion(), 1, QuestionType.SCQ, subject, null, 5, "Вопрос", "Сұрақ", "Разбор", "Түсіндірме", null, null, "Учебник", "not-json", "[\"A\"]"));
            return room.getCode();
        });
        assertThatThrownBy(() -> factory.create(code)).hasMessage("SNAPSHOT_INVALID");
        tx.executeWithoutResult(status -> { var restored = roomRepository.findByCodeIgnoreCase(code).orElseThrow(); assertThat(roundRepository.countByRoomId(restored.getId())).isZero(); assertThat(restored.getStatus()).isEqualTo(QuizRoomStatus.WAITING); assertThat(restored.getAudienceGrades()).isEmpty(); });
    }
    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void outerStartTransactionFailureRollsBackSavedBatchAndAudience() {
        var tx = new TransactionTemplate(transactionManager);
        String code = tx.execute(status -> { var f = factForm(2); counts(f, 2); f.setAudiencePolicy(AudiencePolicy.PARTICIPANT_GRADES); return room(publishGame(f), 2, "ru", 5, 4).getCode(); });
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> { factory.create(code); throw new IllegalStateException("Отказ последующего шага start"); })).hasMessage("Отказ последующего шага start");
        tx.executeWithoutResult(status -> { var restored = roomRepository.findByCodeIgnoreCase(code).orElseThrow(); assertThat(roundRepository.countByRoomId(restored.getId())).isZero(); assertThat(restored.getAudienceGrades()).isEmpty(); assertThat(restored.getStatus()).isEqualTo(QuizRoomStatus.WAITING); });
    }
    private QuizRoom room(QuizGame game, int count, String language, Integer... grades) {
        var version = game.getPublishedVersion(); List<StudentProfile> students = new ArrayList<>();
        for (Integer grade : grades) { var user = users.save(new AppUser(UUID.randomUUID() + "@engine.test", "synthetic", "Ученик", null)); students.add(profiles.save(new StudentProfile(user, grade, language))); }
        var room = roomRepository.save(new QuizRoom(UUID.randomUUID().toString().replace("-", "").substring(0, 12), students.getFirst(), null, null, language, null, count, version.getDefaultRoundSeconds(), version.getMaxPlayers()));
        room.attachGameVersion(version, start.plusMinutes(30));
        for (var student : students) participantRepository.save(new QuizParticipant(room, student, "Ученик " + student.getId(), true));
        entityManager.flush(); return room;
    }
    private Long gradeId(int number) { return grades.findAllByOrderByGradeNoAsc().stream().filter(g -> g.getGradeNo() == number).findFirst().orElseThrow().getId(); }
    private static void counts(QuizGameForm f, int count) { f.setMinQuestionCount(count); f.setDefaultQuestionCount(count); f.setMaxQuestionCount(count); }
    private FactStatementForm factEdit(FactStatement source, boolean truth) {
        var f = new FactStatementForm(); f.setSetId(source.getSet().getId()); f.setTextRu("Новый факт"); f.setTextKk("Жаңа дерек"); f.setTruthValue(truth); f.setExplanationRu("Новый разбор"); f.setExplanationKk("Жаңа түсіндірме"); f.setSourceReference("Новый источник"); f.setUniversalAudience(true); return f;
    }
    private QuestionVersion validSubject(QuestionVersion source, QuestionType type) {
        String options = switch (type) {
            case SCQ, MCQ -> "[{\"label\":\"A\",\"textRu\":\"Один\",\"textKk\":\"Бір\",\"isCorrect\":true,\"explanation\":\"SECRET\"},{\"label\":\"B\",\"textRu\":\"Два\",\"textKk\":\"Екі\"}]";
            case MATCHING -> "[{\"leftRu\":\"50%\",\"leftKk\":\"50 пайыз\",\"rightRu\":\"0.5\",\"rightKk\":\"0.5\"},{\"leftRu\":\"25%\",\"leftKk\":\"25 пайыз\",\"rightRu\":\"0.25\",\"rightKk\":\"0.25\"}]";
            case FILL_IN -> "[]";
        };
        String key = switch (type) { case SCQ -> "[\"B\"]"; case MCQ -> "[\"A\",\"B\"]"; case MATCHING -> "{\"pairs\":[{\"left\":\"50%\",\"right\":\"0.5\"},{\"left\":\"25%\",\"right\":\"0.25\"}]}"; case FILL_IN -> "{\"[[1]]\":{\"answer\":\"30\",\"mode\":\"NUMERIC_TOLERANCE\",\"tolerance\":\"0.1\"}}"; };
        return new QuestionVersion(source.getQuestion(), source.getVersionNo(), type, source.getSubject(), null, source.getDifficulty(), "Вопрос", "Сұрақ", "Разбор", "Түсіндірме", null, null, "Учебник", options, key);
    }
    private JsonNode correctSubjectAnswer(QuestionType type) {
        return json.read(switch (type) { case SCQ -> "{\"selected\":[\"B\"]}"; case MCQ -> "{\"selected\":[\"B\",\"A\"]}"; case MATCHING -> "{\"pairs\":{\"50%\":\"0.5\",\"25%\":\"0.25\"}}"; case FILL_IN -> "{\"answers\":{\"[[1]]\":\"30\"}}"; });
    }
}
