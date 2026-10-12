package kz.damulab.quiz.catalog;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import kz.damulab.questions.QuestionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Приёмка версий, публикационных проверок, источников, аудитории и административной видимости на Flyway-схеме. */
@Transactional
class QuizGameAdminIntegrationTest extends QuizGameTestSupport {
    @Autowired QuizGameVisibilityEventRepository visibility;
    @Autowired QuizContentEditorialEventRepository events;
    @Autowired kz.damulab.config.DamulabQuizProperties properties;

    @Test
    void listUsesBoundedQueriesAndFiltersDraftPublicationLanguageAndErrors() {
        FactSet set = factSet(1); String group = "query-" + UUID.randomUUID();
        QuizGameForm first = form(QuizGameType.TRUE_FALSE); first.setCode(group + "-first"); first.setFactSetId(set.getId());
        games.create(first, admin()); entityManager.flush(); entityManager.clear();
        var stats = entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean previous = stats.isStatisticsEnabled(); stats.setStatisticsEnabled(true);
        try {
            stats.clear(); assertThat(games.list(group, QuizGameType.TRUE_FALSE, EditorialStatus.DRAFT, "kk", false)).hasSize(1);
            long one = stats.getPrepareStatementCount();
            for (int i = 0; i < 4; i++) { QuizGameForm f = form(QuizGameType.TRUE_FALSE); f.setCode(group + "-" + i); f.setFactSetId(set.getId()); games.create(f, admin()); }
            entityManager.flush(); entityManager.clear(); stats.clear();
            assertThat(games.list(group, QuizGameType.TRUE_FALSE, EditorialStatus.DRAFT, "kk", false)).hasSize(5);
            assertThat(stats.getPrepareStatementCount()).as("Количество SQL не растёт на каждую карточку").isLessThanOrEqualTo(one + 1);
            assertThat(games.list(group, QuizGameType.SUBJECT_QUIZ, null, null, null)).isEmpty();
        } finally { stats.setStatisticsEnabled(previous); }
    }

    @Test
    void strictSelfReviewUsesExistingAdminRoleAndKeepsReviewAudit() {
        QuizGame game = games.create(factForm(1), admin()); games.transition(game.getId(), revision(game.getId()), "submit", admin());
        boolean old = properties.isStrictSelfReview(); properties.setStrictSelfReview(true);
        try { assertThatThrownBy(() -> games.transition(game.getId(), revision(game.getId()), "approve", admin())).hasMessageContaining("Автор не может");
            assertThat(game.getDraftVersion().getStatus()).isEqualTo(EditorialStatus.NEEDS_REVIEW);
        } finally { properties.setStrictSelfReview(old); }
        games.transition(game.getId(), revision(game.getId()), "approve", admin()); assertThat(game.getDraftVersion().getReviewedBy()).isEqualTo(admin());
    }

    @Test
    void unpublishedSourceAndDirectionMismatchCannotBePublished() {
        VocabularySetForm sf = new VocabularySetForm(); sf.setCode(code()); sf.setTitleRu("Черновой набор"); sf.setTitleKk("Жоба жинағы");
        sf.setSourceLanguage("en"); sf.setTargetLanguage("ru"); sf.setSourceReference("Словарь"); VocabularySet set = vocabulary.createSet(sf, admin());
        QuizGameForm f = wordForm(set); QuizGame game = createApproved(f);
        assertThatThrownBy(() -> games.publish(game.getId(), revision(game.getId()), true, admin())).hasMessageContaining("набор не опубликован");
        f.setSourceLanguage("kk"); assertThatThrownBy(() -> games.create(f, admin())).hasMessageContaining("Направление");
    }

    @Test
    void threeTypesPublishWithTypedSourcesAndAudit() {
        QuizGame word = publishGame(wordForm(wordSet(4)));
        QuizGame fact = publishGame(factForm(1));
        QuizGame subject = publishGame(subjectForm());
        for (QuizGame game : List.of(word, fact, subject)) {
            assertThat(game.getPublishedVersion().getStatus()).isEqualTo(EditorialStatus.PUBLISHED);
            assertThat(game.getPublishedVersion().getReviewedBy()).isEqualTo(admin());
            assertThat(game.getDraftVersion()).isNull(); assertThat(games.get(game.getId()).errors()).isEmpty();
            assertThat(availability.requireNewRoom(game.getCode()).getId()).isEqualTo(game.getPublishedVersion().getId());
        }
        assertThat(events.findByContentTypeAndContentIdOrderByCreatedAtAsc(QuizContentType.GAME, fact.getId())).hasSize(3);
    }

    @Test
    void newDraftKeepsPublicationAndRoomsPinnedToOldVersion() {
        QuizGame game = publishGame(factForm(1)); Long oldId = game.getPublishedVersion().getId();
        QuizGameForm changed = games.get(game.getId()).form(); changed.setTitleRu("Новая редакция"); games.saveDraft(game.getId(), changed, admin());
        assertThat(game.getPublishedVersion().getId()).isEqualTo(oldId); assertThat(game.getPublishedVersion().getTitleRu()).isEqualTo("Тестовая игра");
        assertThat(game.getDraftVersion().getReviewedAt()).isNull(); approve(game.getId()); games.publish(game.getId(), revision(game.getId()), false, admin());
        assertThat(game.getPublishedVersion().getId()).isNotEqualTo(oldId);
        assertThat(versions.findById(oldId).orElseThrow().getStatus()).isEqualTo(EditorialStatus.PUBLISHED);
        availability.requireWaitingStart(versions.findById(oldId).orElseThrow());
    }

    @Test
    void codeAndTypeAreMutableBeforePublicationOnly() {
        QuizGame game = games.create(factForm(1), admin()); QuizGameForm f = games.get(game.getId()).form(); f.setCode(code());
        VocabularySet set = wordSet(4); f.setGameType(QuizGameType.WORD_TRANSLATION); f.setVocabularySetId(set.getId()); f.setSourceLanguage("en"); f.setTargetLanguage("ru");
        games.saveDraft(game.getId(), f, admin()); approve(game.getId()); games.publish(game.getId(), revision(game.getId()), false, admin());
        QuizGameForm changeCode = games.get(game.getId()).form(); changeCode.setCode(code());
        assertThatThrownBy(() -> games.saveDraft(game.getId(), changeCode, admin())).hasMessageContaining("Код опубликованной");
        QuizGameForm changeType = games.get(game.getId()).form(); changeType.setGameType(QuizGameType.TRUE_FALSE); changeType.setFactSetId(factSet(1).getId());
        assertThatThrownBy(() -> games.saveDraft(game.getId(), changeType, admin())).hasMessageContaining("Тип опубликованной");
    }

    @Test
    void incompatiblePoliciesUnknownGradesAndInvalidLimitsDoNotSave() {
        QuizGameForm f = factForm(1); f.setAudiencePolicy(AudiencePolicy.ROOM_GRADE);
        assertThatThrownBy(() -> games.create(f, admin())).hasMessageContaining("ROOM_GRADE");
        f.setAudiencePolicy(AudiencePolicy.PARTICIPANT_GRADES); f.setGradeSelectionMode(GradeSelectionMode.REQUIRED);
        assertThatThrownBy(() -> games.create(f, admin())).hasMessageContaining("PARTICIPANT_GRADES");
        f.setGradeSelectionMode(GradeSelectionMode.NONE); f.setAudiencePolicy(AudiencePolicy.FIXED_GRADES);
        assertThatThrownBy(() -> games.create(f, admin())).hasMessageContaining("разрешённые классы");
        f.setGradeIds(List.of(-1L)); assertThatThrownBy(() -> games.create(f, admin())).hasMessageContaining("неизвестные");
        f.setGradeIds(List.of(grades.findByGradeNo(1).orElseThrow().getId())); f.setMaxPlayers(8); f.setMinRoundSeconds(100);
        assertThatThrownBy(() -> games.create(f, admin())).hasMessageContaining("Максимум игроков");
    }

    @Test
    void missingTranslationBlocksReviewAndDirectPublication() {
        QuizGameForm f = factForm(1); f.setTitleKk("  "); QuizGame game = games.create(f, admin());
        assertThat(games.get(game.getId()).errors()).anyMatch(e -> e.contains("оба названия"));
        assertThatThrownBy(() -> games.transition(game.getId(), revision(game.getId()), "submit", admin())).hasMessageContaining("оба названия");
        assertThatThrownBy(() -> games.publish(game.getId(), revision(game.getId()), true, admin())).hasMessageContaining("оба названия");
    }

    @Test
    void unapprovedDraftCannotPublishAndWarningsNeedExplicitConfirmation() {
        QuizGameForm f = factForm(1); f.setDefaultQuestionCount(2); f.setMaxQuestionCount(3); QuizGame game = games.create(f, admin());
        assertThatThrownBy(() -> games.publish(game.getId(), revision(game.getId()), true, admin())).hasMessageContaining("переход");
        approve(game.getId()); assertThatThrownBy(() -> games.publish(game.getId(), revision(game.getId()), false, admin())).hasMessageContaining("Подтвердите");
        games.publish(game.getId(), revision(game.getId()), true, admin()); assertThat(game.getPublishedVersion()).isNotNull();
        assertThatThrownBy(() -> games.publish(game.getId(), revision(game.getId()), true, admin())).hasMessageContaining("Нет черновика");
    }

    @Test
    void insufficientDistractorsBlockGameButNotInitialBankPopulation() {
        VocabularySet set = wordSet(3); QuizGame game = createApproved(wordForm(set));
        var result = games.get(game.getId()); assertThat(result.content().worst().published()).isEqualTo(3);
        assertThat(result.content().worst().playable()).isZero();
        assertThat(result.content().worst().excluded()).allMatch(e -> e.reason().contains("Дистракторов"));
        assertThatThrownBy(() -> games.publish(game.getId(), revision(game.getId()), true, admin())).hasMessageContaining("Пригодных вопросов");
    }

    @Test
    void sourceArchivalAfterChecklistIsRecheckedOnPublish() {
        QuizGameForm f = factForm(1); QuizGame game = createApproved(f); assertThat(games.get(game.getId()).errors()).isEmpty();
        facts.archiveSet(f.getFactSetId(), admin());
        assertThatThrownBy(() -> games.publish(game.getId(), revision(game.getId()), true, admin())).hasMessageContaining("Набор утверждений не опубликован");
        assertThat(game.getPublishedVersion()).isNull();
    }

    @Test
    void unverifiedPublicationBlocksButSeparateDraftDoesNot() {
        FactSet set = factSet(1); QuizGameForm f = form(QuizGameType.TRUE_FALSE); f.setFactSetId(set.getId()); QuizGame game = createApproved(f);
        FactStatement s = facts.listStatements().stream().filter(v -> v.getSet().getId().equals(set.getId())).findFirst().orElseThrow();
        FactStatementForm draft = facts.form(s.getId()); draft.setExplanationRu(""); facts.saveDraft(s.getId(), draft, admin());
        assertThat(games.get(game.getId()).errors()).isEmpty();
        entityManager.createNativeQuery("update fact_statement_versions set explanation_ru = '' where id = :id").setParameter("id", s.getPublishedVersion().getId()).executeUpdate(); entityManager.clear();
        assertThatThrownBy(() -> games.publish(game.getId(), revision(game.getId()), true, admin())).hasMessageContaining("неполные материалы");
    }

    @Test
    void fixedGradesChecksIntersectionRatherThanSumOfSeparateAudiences() {
        FactSet set = factSet(0); Long a = grades.findByGradeNo(1).orElseThrow().getId(), b = grades.findByGradeNo(2).orElseThrow().getId();
        publishFact(set, false, List.of(a)); publishFact(set, false, List.of(b));
        QuizGameForm f = form(QuizGameType.TRUE_FALSE); f.setFactSetId(set.getId()); f.setAudiencePolicy(AudiencePolicy.FIXED_GRADES); f.setGradeIds(List.of(a, b));
        QuizGame game = createApproved(f); assertThat(games.get(game.getId()).content().worst().playable()).isZero();
        publishFact(set, false, List.of(a, b)); games.publish(game.getId(), revision(game.getId()), false, admin());
    }

    @Test
    void hideRejectsNewRoomAndReplayButAllowsPinnedWaitingThenArchiveBlocksStart() {
        QuizGame game = publishGame(factForm(1)); QuizGameVersion pinned = game.getPublishedVersion();
        games.setVisibility(game.getId(), revision(game.getId()), false, admin());
        assertThat(gameRepository.findPublishedCatalog(org.springframework.data.domain.Pageable.unpaged())).noneMatch(g -> g.getId().equals(game.getId()));
        assertThatThrownBy(() -> availability.requireNewRoom(game.getCode())).hasMessageContaining("новых комнат"); availability.requireWaitingStart(pinned);
        games.setVisibility(game.getId(), revision(game.getId()), true, admin()); assertThat(availability.requireNewRoom(game.getCode()).getId()).isEqualTo(pinned.getId());
        assertThat(visibility.findByGameIdOrderByIdAsc(game.getId())).hasSize(2); assertThat(game.getVisibilityUpdatedBy()).isEqualTo(admin());
        games.archive(game.getId(), revision(game.getId()), admin()); assertThatThrownBy(() -> availability.requireWaitingStart(pinned)).hasMessageContaining("архивирована");
        assertThat(versions.findById(pinned.getId())).isPresent();
    }

    @Test
    void staleRevisionCannotPublishAnEditedUnreviewedDraft() {
        QuizGame game = createApproved(factForm(1)); long approvedRevision = revision(game.getId());
        QuizGameForm f = games.get(game.getId()).form(); f.setTitleRu("Непроверенная редакция"); games.saveDraft(game.getId(), f, admin());
        assertThatThrownBy(() -> games.publish(game.getId(), approvedRevision, true, admin())).hasMessageContaining("Обновите страницу");
        assertThat(game.getPublishedVersion()).isNull(); assertThat(game.getDraftVersion().getStatus()).isEqualTo(EditorialStatus.DRAFT);
    }

    @Test
    void subjectTypeDifficultyAndEveryRoomGradeHaveSeparateCoverage() {
        QuizGameForm f = subjectForm(); f.setQuestionTypes(Set.of(QuestionType.MCQ)); QuizGame game = createApproved(f);
        assertThat(games.get(game.getId()).content().worst().playable()).isZero();
        QuizGameForm changed = games.get(game.getId()).form(); changed.setQuestionTypes(Set.of(QuestionType.SCQ)); changed.setDifficulty(5);
        games.saveDraft(game.getId(), changed, admin()); assertThat(games.get(game.getId()).content().worst().playable()).isZero();
        changed = games.get(game.getId()).form(); changed.setDifficulty(1); games.saveDraft(game.getId(), changed, admin());
        assertThat(games.get(game.getId()).content().worst().playable()).isEqualTo(1);
    }

    private QuizGameForm wordForm(VocabularySet set) { QuizGameForm f = form(QuizGameType.WORD_TRANSLATION); f.setVocabularySetId(set.getId()); return f; }
    private QuizGameForm subjectForm() {
        var subject = subjects.findAll().getFirst(); Long grade = grades.findByGradeNo(1).orElseThrow().getId(); subjectQuestion(subject, QuestionType.SCQ, 1, List.of(grade));
        QuizGameForm f = form(QuizGameType.SUBJECT_QUIZ); f.setSubjectId(subject.getId()); f.setGradeSelectionMode(GradeSelectionMode.REQUIRED); f.setAudiencePolicy(AudiencePolicy.ROOM_GRADE); f.setGradeIds(List.of(grade)); return f;
    }
}
