package kz.damulab;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.*;
import kz.damulab.users.*;
import kz.damulab.questions.QuestionType;
import kz.damulab.content.Subject;

/**
 * Проверяет реальные JPA-отображения и JPQL foundation, а не mock репозиториев.
 * H2 используется по умолчанию, QUIZ_TEST_DB_* переключает этот же набор на PostgreSQL.
 * Транзакции откатывают фикстуры; состояние рабочего приложения не меняется.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true", showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class QuizFoundationRepositoryTest {
    @Autowired EntityManager em;
    @Autowired QuizGameRepository games;
    @Autowired VocabularyItemVersionRepository words;
    @Autowired FactStatementVersionRepository facts;
    @Autowired VocabularyItemAnswerRepository translations;
    @Autowired VocabularyDistractorExclusionRepository exclusions;
    @Autowired QuizRoomRepository rooms;
    @Autowired QuizRoomCreationKeyRepository keys;
    @Autowired QuizSubjectGameConfigRepository subjectConfigs;
    @Autowired QuizWordGameConfigRepository wordConfigs;
    @Autowired QuizFactGameConfigRepository factConfigs;

    @Test
    void catalogLoadsPublishedVersionsInOneQueryRegardlessOfCardCount() {
        for (int i = 0; i < 6; i++) game(QuizGameType.TRUE_FALSE, true);
        QuizGame hidden = game(QuizGameType.TRUE_FALSE, true); hidden.setCatalogVisible(false);
        QuizGame archived = game(QuizGameType.TRUE_FALSE, true); archived.setStatus(EditorialStatus.ARCHIVED);
        game(QuizGameType.SUBJECT_QUIZ, false);
        flushClear();
        var stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var catalog = games.findPublishedCatalog(PageRequest.of(0, 100));
        assertThat(catalog).hasSize(6);
        assertThat(catalog).allSatisfy(g -> assertThat(g.getPublishedVersion().getTitleRu()).isEqualTo("Игра"));
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void typedConfigsAndReviewAuditRoundTripThroughJpa() {
        AppUser reviewer = user();
        QuizGame subjectGame = game(QuizGameType.SUBJECT_QUIZ, false);
        var version = subjectGame.getDraftVersion();
        version.setCreatedBy(reviewer.getId()); version.recordReview(reviewer.getId(), OffsetDateTime.now());
        version.setGradeIds(Set.of(grade(1)));
        Subject subject = em.createQuery("select s from Subject s where s.code = 'math'", Subject.class).getSingleResult();
        var config = new QuizSubjectGameConfig(version, subject, 2);
        config.setQuestionTypes(Set.of(QuestionType.SCQ, QuestionType.MCQ));
        em.persist(config);

        VocabularySet set = vocabularySet();
        QuizGame wordGame = game(QuizGameType.WORD_TRANSLATION, false);
        em.persist(new QuizWordGameConfig(wordGame.getDraftVersion(), set, "en", "ru"));
        FactSet factSet = factSet();
        QuizGame factGame = game(QuizGameType.TRUE_FALSE, false);
        em.persist(new QuizFactGameConfig(factGame.getDraftVersion(), factSet));
        Long versionId = version.getId(); Long wordId = wordGame.getDraftVersion().getId(); Long factId = factGame.getDraftVersion().getId();
        flushClear();
        var loaded = subjectConfigs.findByGameVersionIdIn(List.of(versionId)).getFirst();
        assertThat(loaded.getQuestionTypes()).containsExactlyInAnyOrder(QuestionType.SCQ, QuestionType.MCQ);
        assertThat(loaded.getSubject().getCode()).isEqualTo("math");
        assertThat(loaded.getGameVersion().getReviewedBy()).isEqualTo(reviewer.getId());
        assertThat(loaded.getGameVersion().getReviewedAt()).isNotNull();
        assertThat(loaded.getGameVersion().getGradeIds()).containsExactly(grade(1));
        assertThat(wordConfigs.findByGameVersionIdIn(List.of(wordId)).getFirst().getVocabularySet().getSourceLanguage()).isEqualTo("en");
        assertThat(factConfigs.findByGameVersionIdIn(List.of(factId)).getFirst().getFactSet().getTitleRu()).isEqualTo("Факты");
    }

    @Test
    void vocabularyEligibilityIsIntersectionAndEmptyGradesAreNotUniversal() {
        VocabularySet set = vocabularySet();
        VocabularyItemVersion universal = word(set, true, Set.of(), true);
        word(set, false, Set.of(grade(1)), true);
        VocabularyItemVersion both = word(set, false, Set.of(grade(1), grade(2)), true);
        word(set, false, Set.of(), true);
        word(set, true, Set.of(), false);
        VocabularyItemVersion archived = word(set, true, Set.of(), true);
        archived.getItem().setStatus(EditorialStatus.ARCHIVED);
        Long setId = set.getId(), universalId = universal.getId(), bothId = both.getId();
        flushClear();
        assertThat(words.countEligible(setId, Set.of())).isEqualTo(1);
        assertThat(words.countEligible(setId, Set.of(grade(1)))).isEqualTo(3);
        assertThat(words.countEligible(setId, Set.of(grade(1), grade(2)))).isEqualTo(2);
        assertThat(words.findEligibleIds(setId, Set.of(grade(1), grade(2)), PageRequest.of(0, 100))).containsExactly(universalId, bothId);
        assertThat(words.findByNormalizedSource(setId, "house")).hasSize(6);
        em.find(VocabularySet.class, setId).setStatus(EditorialStatus.ARCHIVED); em.flush();
        assertThat(words.countEligible(setId, Set.of())).isZero();
    }

    @Test
    void unpublishedSetDoesNotExposePublishedWordsAndDraftDoesNotHideCurrentPublication() {
        VocabularySet set = vocabularySet();
        word(set, true, Set.of(), true);
        VocabularySetVersion publication = set.getPublishedVersion();
        set.setPublishedVersion(null); em.flush();
        assertThat(words.countEligible(set.getId(), Set.of())).isZero();
        set.setPublishedVersion(publication);
        var draft = new VocabularySetVersion(2, set, "Черновик", "Жоба", "Источник");
        em.persist(draft); set.setDraftVersion(draft); em.flush();
        assertThat(words.countEligible(set.getId(), Set.of())).isEqualTo(1);
    }

    @Test
    void factQueriesKeepFalseAndFilterCurrentPublicationAndAllGrades() {
        FactSet set = factSet();
        FactStatementVersion universal = fact(set, true, Set.of(), true);
        fact(set, false, Set.of(grade(1)), true);
        FactStatementVersion both = fact(set, false, Set.of(grade(1), grade(2)), true);
        fact(set, false, Set.of(), true);
        fact(set, true, Set.of(), false);
        Long setId = set.getId(), universalId = universal.getId(), bothId = both.getId();
        flushClear();
        assertThat(facts.countEligible(setId, Set.of())).isEqualTo(1);
        assertThat(facts.countEligible(setId, Set.of(grade(1), grade(2)))).isEqualTo(2);
        assertThat(facts.findEligibleIds(setId, Set.of(grade(1), grade(2)), PageRequest.of(0, 10))).containsExactly(universalId, bothId);
        assertThat(em.find(FactStatementVersion.class, universalId).isTruthValue()).isFalse();
        em.find(FactSet.class, setId).setStatus(EditorialStatus.ARCHIVED); em.flush();
        assertThat(facts.countEligible(setId, Set.of())).isZero();
    }

    @Test
    void translationsAndExclusionsLoadInBatchesAndBothDirections() {
        VocabularySet set = vocabularySet();
        var first = word(set, true, Set.of(), true);
        var second = word(set, true, Set.of(), true);
        em.persist(new VocabularyItemAnswer(first, "Дом", "дом", true));
        em.persist(new VocabularyItemAnswer(first, "Жильё", "жильё", false));
        em.persist(new VocabularyItemAnswer(second, "Дом", "дом", true));
        em.persist(new VocabularyDistractorExclusion(first, second, "Одинаковый перевод"));
        Long firstId = first.getId(), secondId = second.getId(); flushClear();
        assertThat(translations.findByItemVersionIdIn(List.of(firstId, secondId))).hasSize(3);
        var reverse = exclusions.findForVersions(List.of(secondId));
        assertThat(reverse).hasSize(1);
        assertThat(reverse.getFirst().getItemVersion().getId()).isEqualTo(firstId);
        assertThat(reverse.getFirst().getExcludedItemVersion().getId()).isEqualTo(secondId);
    }

    @Test
    void roomSnapshotsTimingReadyRevisionAndCreateKeyRoundTrip() {
        var room = room();
        var game = game(QuizGameType.TRUE_FALSE, true);
        room.attachGameVersion(game.getPublishedVersion(), OffsetDateTime.now().plusHours(1));
        room.changeSettings(1, Set.of(1, 2), 5, 20);
        var guest = new QuizParticipant(room, room.getHostStudentProfile(), "Ученик", false); em.persist(guest);
        assertThatThrownBy(() -> guest.markReady(0)).isInstanceOf(IllegalStateException.class);
        guest.markReady(1); assertThat(guest.getReadyRevision()).isEqualTo(1L);
        guest.resetReady(); assertThat(guest.isReady()).isFalse(); guest.markReady(1);
        var source = fact(factSet(), true, Set.of(), true);
        var round = QuizRound.fact(room, source, 1);
        round.snapshot("Утверждение", "[]", "false", "Объяснение", "Учебник", "{}", "kk"); em.persist(round);
        var answer = new QuizAnswer(round, guest, "false", false, BigDecimal.ZERO);
        answer.recordTiming(20000, false, true); em.persist(answer);
        assertThatThrownBy(() -> answer.replace("true", true, BigDecimal.ONE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> answer.recordTiming(1, false, false)).isInstanceOf(IllegalStateException.class);
        var key = new QuizRoomCreationKey(room.getHostStudentProfile(), "a".repeat(64), "b".repeat(64), room); em.persist(key);
        Long roomId = room.getId(), roundId = round.getId(), answerId = answer.getId(), profileId = room.getHostStudentProfile().getId();
        flushClear();
        QuizRoom loaded = em.find(QuizRoom.class, roomId);
        assertThat(loaded.getGameType()).isEqualTo(QuizGameType.TRUE_FALSE);
        assertThat(loaded.getSettingsRevision()).isEqualTo(1);
        assertThat(loaded.getAudienceGrades()).containsExactlyInAnyOrder(1, 2);
        assertThat(em.find(QuizRound.class, roundId).getQuestionVersion()).isNull();
        assertThat(em.find(QuizRound.class, roundId).getAnswerKeySnapshotJson()).isEqualTo("false");
        assertThat(em.find(QuizRound.class, roundId).getContentLocale()).isEqualTo("kk");
        assertThat(em.find(QuizAnswer.class, answerId).isSkipped()).isTrue();
        assertThat(em.find(QuizAnswer.class, answerId).getResponseMs()).isEqualTo(20000L);
        assertThat(keys.findByStudentProfileIdAndKeyHash(profileId, "a".repeat(64)).orElseThrow().getRoom().getId()).isEqualTo(roomId);
    }

    @Test
    void vocabularyRoundReferencesActualSetRevision() {
        var room = room(); var game = game(QuizGameType.WORD_TRANSLATION, true);
        room.attachGameVersion(game.getPublishedVersion(), OffsetDateTime.now().plusHours(1));
        var set = vocabularySet(); var source = word(set, true, Set.of(), true);
        var round = QuizRound.vocabulary(room, source, set.getPublishedVersion(), 1);
        round.snapshot("house", "[1,2,3,4]", "1", "Значение", "Учебник", "{}", "ru"); em.persist(round);
        Long id = round.getId(), setVersionId = set.getPublishedVersion().getId(); flushClear();
        var loaded = em.find(QuizRound.class, id);
        assertThat(loaded.getContentKind()).isEqualTo(QuizContentKind.VOCABULARY);
        assertThat(loaded.getVocabularySetVersion().getId()).isEqualTo(setVersionId);
        assertThatThrownBy(() -> loaded.snapshot("", "", "", "", "", "", "")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiryQueryIgnoresLegacyAndActiveAndLockUsesCanonicalCode() {
        var now = OffsetDateTime.now();
        var legacy = room();
        var waiting = room(); waiting.attachGameVersion(game(QuizGameType.TRUE_FALSE, true).getPublishedVersion(), now.minusMinutes(1));
        var active = room(); active.attachGameVersion(game(QuizGameType.TRUE_FALSE, true).getPublishedVersion(), now.minusMinutes(1)); active.start(now);
        var future = room(); future.attachGameVersion(game(QuizGameType.TRUE_FALSE, true).getPublishedVersion(), now.plusHours(1));
        Long waitingId = waiting.getId(), legacyId = legacy.getId(); String code = waiting.getCode(); flushClear();
        assertThat(rooms.findExpiredWaiting(now, PageRequest.of(0, 100))).extracting(QuizRoom::getId).containsExactly(waitingId);
        assertThat(rooms.findLockedByCode(code.toLowerCase()).orElseThrow().getId()).isEqualTo(waitingId);
        var loaded = em.find(QuizRoom.class, waitingId); loaded.expire(now);
        assertThat(loaded.getStatus()).isEqualTo(QuizRoomStatus.EXPIRED);
        assertThat(loaded.getGameVersion()).isNotNull();
        em.find(QuizRoom.class, legacyId).expire(now);
        assertThat(em.find(QuizRoom.class, legacyId).getStatus()).isEqualTo(QuizRoomStatus.WAITING);
    }

    @Test
    void cancelAndTimingValidationDoNotChangeInvalidRecords() {
        var room = room(); room.cancel(OffsetDateTime.now());
        assertThat(room.getStatus()).isEqualTo(QuizRoomStatus.CANCELLED);
        assertThat(room.getCancelledAt()).isNotNull();
        assertThatThrownBy(() -> room.finish(OffsetDateTime.now())).isInstanceOf(IllegalStateException.class);
        assertThat(room.getStatus()).isEqualTo(QuizRoomStatus.CANCELLED);
        assertThatThrownBy(() -> room.changeSettings(null, Set.of(), 10, 20)).isInstanceOf(IllegalStateException.class);
        var participant = new QuizParticipant(room, room.getHostStudentProfile(), "Тест", false);
        var round = QuizRound.fact(room, fact(factSet(), true, Set.of(), true), 1);
        var answer = new QuizAnswer(round, participant, "false", true, BigDecimal.ONE);
        assertThatThrownBy(() -> answer.recordTiming(20000, true, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> answer.recordTiming(-1, false, false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(answer.getResponseMs()).isNull();
    }

    @Test
    void incompleteSnapshotDoesNotPartiallyMutateRoundAndCanBeCorrected() {
        var round = QuizRound.fact(room(), fact(factSet(), true, Set.of(), true), 1);
        assertThatThrownBy(() -> round.snapshot("Текст", "[]", "false", "Объяснение", "Источник", "{}", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(round.getPromptSnapshot()).isNull();
        assertThat(round.getAnswerKeySnapshotJson()).isNull();
        round.snapshot("Текст", "[]", "false", "Объяснение", "Источник", "{}", "ru");
        em.persist(round); em.flush();
        assertThat(round.getPromptSnapshot()).isEqualTo("Текст");
    }

    private void flushClear() { em.flush(); em.clear(); }
    private Long grade(int gradeNo) {
        return em.createQuery("select g.id from Grade g where g.gradeNo = :no", Long.class).setParameter("no", gradeNo).getSingleResult();
    }
    private AppUser user() {
        var user = new AppUser(UUID.randomUUID() + "@example.test", "test-only", "Тест", null); em.persist(user); return user;
    }
    private QuizRoom room() {
        var profile = new StudentProfile(user(), 1, "ru"); em.persist(profile);
        var room = new QuizRoom(UUID.randomUUID().toString().substring(0, 12).toUpperCase(), profile, null, null, "ru", null, 10, 20, 4);
        em.persist(room); return room;
    }
    private QuizGame game(QuizGameType type, boolean publish) {
        var game = new QuizGame(UUID.randomUUID().toString()); em.persist(game);
        var version = new QuizGameVersion(1, game, type, "Игра", "Ойын"); em.persist(version);
        if (publish) { version.setStatus(EditorialStatus.PUBLISHED); version.setPublishedAt(OffsetDateTime.now()); game.setPublishedVersion(version); game.setStatus(EditorialStatus.PUBLISHED); }
        else game.setDraftVersion(version);
        return game;
    }
    private VocabularySet vocabularySet() {
        var set = new VocabularySet(UUID.randomUUID().toString(), "en", "ru"); em.persist(set);
        var version = new VocabularySetVersion(1, set, "Набор", "Жинақ", "Учебник");
        version.setStatus(EditorialStatus.PUBLISHED); version.setPublishedAt(OffsetDateTime.now()); em.persist(version);
        set.setPublishedVersion(version); set.setStatus(EditorialStatus.PUBLISHED); return set;
    }
    private VocabularyItemVersion word(VocabularySet set, boolean universal, Set<Long> grades, boolean publish) {
        var item = new VocabularyItem(set); em.persist(item);
        var version = new VocabularyItemVersion(1, item, "house", "house", "Учебник");
        version.setUniversalAudience(universal); version.setGradeIds(grades); em.persist(version);
        if (publish) { version.setStatus(EditorialStatus.PUBLISHED); version.setPublishedAt(OffsetDateTime.now()); item.setPublishedVersion(version); }
        else item.setDraftVersion(version);
        return version;
    }
    private FactSet factSet() {
        var set = new FactSet(UUID.randomUUID().toString(), "Факты", "Деректер", "Учебник"); em.persist(set); return set;
    }
    private FactStatementVersion fact(FactSet set, boolean universal, Set<Long> grades, boolean publish) {
        var statement = new FactStatement(set); em.persist(statement);
        var version = new FactStatementVersion(1, statement, "Утверждение", "Тұжырым", false, "Объяснение", "Түсіндірме", "Учебник");
        version.setUniversalAudience(universal); version.setGradeIds(grades); em.persist(version);
        if (publish) { version.setStatus(EditorialStatus.PUBLISHED); version.setPublishedAt(OffsetDateTime.now()); statement.setPublishedVersion(version); }
        else statement.setDraftVersion(version);
        return version;
    }
}
