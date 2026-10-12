package kz.damulab.quiz.catalog;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import kz.damulab.content.GradeRepository;
import kz.damulab.users.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Репозиторно-сервисная приёмка workflow банков на настоящей Flyway-схеме. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuizContentBankIntegrationTest {
    @Autowired VocabularyAdminService vocabulary;
    @Autowired FactAdminService facts;
    @Autowired VocabularyDistractorService distractors;
    @Autowired VocabularyItemAnswerRepository answers;
    @Autowired QuizContentEditorialEventRepository events;
    @Autowired GradeRepository grades;
    @Autowired AppUserRepository users;

    @Test
    void russianToEnglishSetCanBeEditedAndPublishedThroughNormalWorkflow() {
        Long admin = adminId();
        VocabularySetForm form = setForm(false);
        form.setSourceLanguage("ru"); form.setTargetLanguage("en");
        VocabularySet set = vocabulary.createSet(form, admin);
        vocabulary.saveSetDraft(set.getId(), form, admin);
        vocabulary.submitSet(set.getId(), admin);
        vocabulary.approveSet(set.getId(), admin);
        vocabulary.publishSet(set.getId(), admin);
        assertThat(vocabulary.requireSet(set.getId()).getPublishedVersion()).isNotNull();
        assertThat(vocabulary.setForm(set.getId()).getTargetLanguage()).isEqualTo("en");
    }

    @Test
    void firstQualityWordPublishesWithoutExistingDistractorsAndNewDraftKeepsPublication() {
        Long admin = adminId(); VocabularySet set = publishedVocabularySet(admin, false);
        VocabularyItem item = vocabulary.createItem(wordForm(set.getId(), "  Discover  ", "обнаружить", "открыть", true), admin);
        vocabulary.submitItem(item.getId(), admin); vocabulary.approveItem(item.getId(), admin); vocabulary.publishItem(item.getId(), admin);

        VocabularyItem published = vocabulary.requireItem(item.getId()); Long publishedVersion = published.getPublishedVersion().getId();
        assertThat(published.getPublishedVersion().getSourceText()).isEqualTo("Discover");
        assertThat(published.getPublishedVersion().getSourceNormalized()).isEqualTo("discover");
        assertThat(distractors.diagnose(publishedVersion, Set.of()).playable()).isFalse();

        VocabularyItemForm changed = wordForm(set.getId(), "Discover!", "обнаружить", "открыть", true);
        vocabulary.saveItemDraft(item.getId(), changed, admin);
        VocabularyItem afterDraft = vocabulary.requireItem(item.getId());
        assertThat(afterDraft.getPublishedVersion().getId()).isEqualTo(publishedVersion);
        assertThat(afterDraft.getPublishedVersion().getSourceText()).isEqualTo("Discover");
        assertThat(afterDraft.getDraftVersion().getId()).isNotEqualTo(publishedVersion);
    }

    @Test
    void homonymsRemainSeparateAndDuplicateSearchUsesSourceOrTranslation() {
        Long admin = adminId(); VocabularySet set = vocabulary.createSet(setForm(false), admin);
        VocabularyItem first = vocabulary.createItem(wordForm(set.getId(), "bank", "банк", "", true), admin);
        VocabularyItem second = vocabulary.createItem(wordForm(set.getId(), " BANK ", "берег", "", true), admin);
        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(vocabulary.findPotentialDuplicates(set.getId(), "  BaNk ")).hasSize(2);
        assertThat(vocabulary.findPotentialDuplicates(set.getId(), " БЕРЕГ ")).extracting(VocabularyItemVersion::getItem)
                .extracting(VocabularyItem::getId).containsExactly(second.getId());
    }

    @Test
    void explicitAudienceIsRequired() {
        Long admin = adminId(); VocabularySet set = vocabulary.createSet(setForm(false), admin);
        VocabularyItem item = vocabulary.createItem(wordForm(set.getId(), "word", "слово", "", false), admin);
        assertThatThrownBy(() -> vocabulary.submitItem(item.getId(), admin))
                .isInstanceOf(ContentValidationException.class)
                .hasMessageContaining("универсальную аудиторию");
    }

    @Test
    void primaryAndAlternativesMustDifferAfterNormalization() {
        Long admin = adminId(); VocabularySet set = vocabulary.createSet(setForm(false), admin);
        VocabularyItemForm invalid = wordForm(set.getId(), "word", "слово", " СЛОВО ", true);
        assertThatThrownBy(() -> vocabulary.createItem(invalid, admin)).isInstanceOf(ContentValidationException.class)
                .hasMessageContaining("различаться после нормализации");
    }

    @Test
    void distractorsFilterAlternativesExclusionsAndNeedThreeCandidates() {
        Long admin = adminId(); VocabularySet set = publishedVocabularySet(admin, false);
        VocabularyItem source = publishWord(set, "quick", "быстрый", "скорый", admin);
        VocabularyItem ambiguous = publishWord(set, "rapid", "скорый", "", admin);
        VocabularyItem a = publishWord(set, "slow", "медленный", "", admin);
        VocabularyItem b = publishWord(set, "calm", "спокойный", "", admin);
        VocabularyItem c = publishWord(set, "bright", "яркий", "", admin);

        var ready = distractors.diagnose(source.getPublishedVersion().getId(), Set.of());
        assertThat(ready.playable()).isTrue();
        assertThat(ready.excludedByReason()).containsEntry("AMBIGUOUS_TRANSLATION", 1);
        distractors.exclude(source.getPublishedVersion().getId(), c.getPublishedVersion().getId(), "Слишком близкий контекст", admin);
        var shortfall = distractors.diagnose(source.getPublishedVersion().getId(), Set.of());
        assertThat(shortfall.playable()).isFalse();
        assertThat(shortfall.candidates()).extracting(VocabularyDistractorService.Candidate::versionId)
                .containsExactlyInAnyOrder(a.getPublishedVersion().getId(), b.getPublishedVersion().getId());
        assertThat(shortfall.candidates()).noneMatch(v -> v.versionId().equals(ambiguous.getPublishedVersion().getId()));
    }

    @Test
    void crossSetDistractorsNeedExplicitPermissionAndAudienceCoversEveryGrade() {
        Long admin = adminId(); VocabularySet sourceSet = publishedVocabularySet(admin, false);
        VocabularySet otherSet = publishedVocabularySet(admin, false);
        VocabularyItem source = publishWord(sourceSet, "source", "источник", "", admin);

        VocabularyItemForm candidateForm = wordForm(otherSet.getId(), "candidate", "кандидат", "", false);
        candidateForm.setGradeIds(List.of(grades.findByGradeNo(1).orElseThrow().getId()));
        VocabularyItem candidate = vocabulary.createItem(candidateForm, admin);
        vocabulary.submitItem(candidate.getId(), admin); vocabulary.approveItem(candidate.getId(), admin); vocabulary.publishItem(candidate.getId(), admin);

        var disabled = distractors.diagnose(source.getPublishedVersion().getId(), Set.of(grades.findByGradeNo(1).orElseThrow().getId()));
        // Системный пакет тоже содержит опубликованные слова: проверяем конкретный запрет,
        // а не предположение, что вся БД состоит только из двух наборов этого теста.
        assertThat(disabled.excludedByReason().get("CROSS_SET_DISABLED")).isGreaterThanOrEqualTo(1);
        assertThat(disabled.candidates()).extracting(VocabularyDistractorService.Candidate::versionId)
                .doesNotContain(candidate.getPublishedVersion().getId());

        VocabularySetForm changed = vocabulary.setForm(sourceSet.getId()); changed.setCrossSetDistractorsAllowed(true);
        vocabulary.saveSetDraft(sourceSet.getId(), changed, admin); vocabulary.submitSet(sourceSet.getId(), admin);
        vocabulary.approveSet(sourceSet.getId(), admin); vocabulary.publishSet(sourceSet.getId(), admin);
        var oneGrade = distractors.diagnose(source.getPublishedVersion().getId(), Set.of(grades.findByGradeNo(1).orElseThrow().getId()));
        assertThat(oneGrade.candidates()).extracting(VocabularyDistractorService.Candidate::versionId).contains(candidate.getPublishedVersion().getId());

        var twoGrades = distractors.diagnose(source.getPublishedVersion().getId(), Set.of(
                grades.findByGradeNo(1).orElseThrow().getId(), grades.findByGradeNo(2).orElseThrow().getId()));
        assertThat(twoGrades.excludedByReason()).containsEntry("AUDIENCE", 1);
    }

    @Test
    void falseFactPublishesAndNewDraftDoesNotChangePublishedVersion() {
        Long admin = adminId(); FactSet set = publishedFactSet(admin);
        FactStatementForm form = factForm(set.getId(), false, "Потому что источник это подтверждает", "Өйткені дереккөз растайды", "https://example.test/source");
        FactStatement statement = facts.createStatement(form, admin);
        facts.submit(statement.getId(), admin); facts.approve(statement.getId(), admin); facts.publish(statement.getId(), admin);
        FactStatement published = facts.requireStatement(statement.getId()); Long versionId = published.getPublishedVersion().getId();
        assertThat(published.getPublishedVersion().isTruthValue()).isFalse();

        FactStatementForm changed = factForm(set.getId(), true, "Новое объяснение", "Жаңа түсініктеме", "https://example.test/new");
        facts.saveDraft(statement.getId(), changed, admin);
        assertThat(facts.requireStatement(statement.getId()).getPublishedVersion().getId()).isEqualTo(versionId);
        assertThat(facts.requireStatement(statement.getId()).getPublishedVersion().isTruthValue()).isFalse();
    }

    @Test
    void factWithoutExplanationOrSourceCannotBeSubmittedAndArchiveKeepsVersions() {
        Long admin = adminId(); FactSet set = facts.createSet(factSetForm(), admin);
        FactStatement statement = facts.createStatement(factForm(set.getId(), false, "", "", ""), admin);
        assertThatThrownBy(() -> facts.submit(statement.getId(), admin)).isInstanceOf(ContentValidationException.class)
                .hasMessageContaining("объяснение").hasMessageContaining("источник");

        FactStatement good = facts.createStatement(factForm(set.getId(), true, "Объяснение", "Түсініктеме", "Источник"), admin);
        facts.submit(good.getId(), admin); facts.approve(good.getId(), admin); facts.publish(good.getId(), admin);
        Long versionId = facts.requireStatement(good.getId()).getPublishedVersion().getId(); facts.archiveStatement(good.getId(), admin);
        assertThat(facts.requireStatement(good.getId()).getStatus()).isEqualTo(EditorialStatus.ARCHIVED);
        assertThat(facts.requireStatement(good.getId()).getPublishedVersion().getId()).isEqualTo(versionId);
        assertThat(events.findByContentTypeAndContentIdOrderByCreatedAtAsc(QuizContentType.FACT_STATEMENT, good.getId()))
                .extracting(QuizContentEditorialEvent::getToStatus)
                .containsExactly(EditorialStatus.NEEDS_REVIEW, EditorialStatus.APPROVED, EditorialStatus.PUBLISHED, EditorialStatus.ARCHIVED);
    }

    private VocabularySet publishedVocabularySet(Long admin, boolean crossSet) {
        VocabularySet set = vocabulary.createSet(setForm(crossSet), admin);
        vocabulary.submitSet(set.getId(), admin); vocabulary.approveSet(set.getId(), admin); vocabulary.publishSet(set.getId(), admin);
        return vocabulary.requireSet(set.getId());
    }
    private VocabularyItem publishWord(VocabularySet set, String source, String primary, String alternatives, Long admin) {
        VocabularyItem item = vocabulary.createItem(wordForm(set.getId(), source, primary, alternatives, true), admin);
        vocabulary.submitItem(item.getId(), admin); vocabulary.approveItem(item.getId(), admin); vocabulary.publishItem(item.getId(), admin);
        return vocabulary.requireItem(item.getId());
    }
    private FactSet publishedFactSet(Long admin) {
        FactSet set = facts.createSet(factSetForm(), admin); facts.transitionSet(set.getId(), EditorialStatus.NEEDS_REVIEW, admin);
        facts.transitionSet(set.getId(), EditorialStatus.APPROVED, admin); facts.transitionSet(set.getId(), EditorialStatus.PUBLISHED, admin); return facts.requireSet(set.getId());
    }
    private VocabularySetForm setForm(boolean crossSet) { VocabularySetForm f = new VocabularySetForm(); f.setCode("set-" + UUID.randomUUID()); f.setSourceLanguage("en"); f.setTargetLanguage("ru"); f.setTitleRu("Набор"); f.setTitleKk("Жинақ"); f.setSourceReference("Oxford test source"); f.setCrossSetDistractorsAllowed(crossSet); return f; }
    private VocabularyItemForm wordForm(Long setId, String source, String primary, String alternatives, boolean universal) { VocabularyItemForm f = new VocabularyItemForm(); f.setSetId(setId); f.setSourceText(source); f.setPrimaryAnswer(primary); f.setAlternativeAnswers(alternatives); f.setMeaningNoteRu("Одно учебное значение"); f.setDifficulty(2); f.setSourceReference("Dictionary source"); f.setUniversalAudience(universal); return f; }
    private FactSetForm factSetForm() { FactSetForm f = new FactSetForm(); f.setCode("facts-" + UUID.randomUUID()); f.setTitleRu("Факты"); f.setTitleKk("Деректер"); f.setSourcePolicy("Проверяемые первичные источники"); return f; }
    private FactStatementForm factForm(Long setId, boolean truth, String explanationRu, String explanationKk, String source) { FactStatementForm f = new FactStatementForm(); f.setSetId(setId); f.setTextRu("Тестовое утверждение"); f.setTextKk("Сынақ тұжырымы"); f.setTruthValue(truth); f.setExplanationRu(explanationRu); f.setExplanationKk(explanationKk); f.setSourceReference(source); f.setUniversalAudience(true); return f; }
    private Long adminId() { return users.findByEmailIgnoreCase("admin@damulab.kz").orElseThrow().getId(); }
}
