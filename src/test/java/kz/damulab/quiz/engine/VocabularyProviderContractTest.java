package kz.damulab.quiz.engine;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.OffsetDateTime;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

/** Изолированный словарный банк: альтернативы обоих слов, запреты, направление, все классы и opt-in cross-set. */
class VocabularyProviderContractTest {
    private final VocabularyItemVersionRepository versions = mock(VocabularyItemVersionRepository.class);
    private final VocabularyItemAnswerRepository answers = mock(VocabularyItemAnswerRepository.class);
    private final VocabularyDistractorExclusionRepository exclusions = mock(VocabularyDistractorExclusionRepository.class);
    private final QuizWordGameConfigRepository configs = mock(QuizWordGameConfigRepository.class);
    private final VocabularyDistractorService distractors = new VocabularyDistractorService(versions, answers, exclusions);
    private final QuizSnapshotJson json = new QuizSnapshotJson(new ObjectMapper());
    private final VocabularyRoundProvider provider = new VocabularyRoundProvider(configs, distractors, json);
    private final VocabularySet local = set(1, "en", "ru");
    private final VocabularySet cross = set(2, "en", "ru");
    private final List<VocabularyItemVersion> bank = new ArrayList<>();
    private final List<VocabularyItemAnswer> translations = new ArrayList<>();
    private final List<VocabularyDistractorExclusion> forbidden = new ArrayList<>();
    private QuizRoom room;

    @BeforeEach void prepare() {
        var v = new QuizGameVersion(1, null, QuizGameType.WORD_TRANSLATION, "Слова", "Сөздер"); id(v, 1L);
        room = new QuizRoom("TEST", null, null, null, "ru", null, 1, 30, 4); room.attachGameVersion(v, OffsetDateTime.now().plusMinutes(30));
        when(configs.findById(1L)).thenReturn(Optional.of(new QuizWordGameConfig(v, local, "en", "ru")));
        when(versions.findPublishedCurrentVersions()).thenReturn(bank);
        when(versions.findPublishedForGame(any(), any(), any(), anyBoolean())).thenReturn(bank);
        when(answers.findByItemVersionIdIn(any())).thenReturn(translations);
        when(exclusions.findForVersions(any())).thenReturn(forbidden);
    }
    @Test void primaryAndAlternativesOfEitherSideCannotBecomeDistractors() {
        var source = word(1, local, "good", "right"); translations.add(new VocabularyItemAnswer(source, "alternative", "alternative", false));
        word(2, local, "bad1", "alternative"); var synonym = word(3, local, "bad2", "other-primary"); translations.add(new VocabularyItemAnswer(synonym, "right", "right", false));
        for (int i = 4; i < 7; i++) word(i, local, "word" + i, "answer" + i);
        var diagnosis = distractors.diagnose(source, Set.of(), distractors.prepare(List.of()));
        assertThat(diagnosis.candidates()).extracting(VocabularyDistractorService.Candidate::versionId).containsExactly(4L, 5L, 6L);
        assertThat(diagnosis.excludedByReason().get("AMBIGUOUS_TRANSLATION")).isEqualTo(2);
        var prepared = provider.prepare(room, Set.of()); assertThat(prepared).extracting(QuizRoundProvider.Source::versionId).contains(1L);
        var round = prepared.stream().filter(s -> s.versionId() == 1L).findFirst().orElseThrow().builder().build(1, new Random(1));
        assertThat(round.getOptionsSnapshotJson()).doesNotContain("alternative", "other-primary", "isCorrect");
    }
    @Test void crossSetRequiresExplicitCurrentPublishedOptIn() {
        var source = word(1, local, "good", "right"); for (int i = 2; i < 5; i++) word(i, cross, "word" + i, "answer" + i);
        assertThat(provider.prepare(room, Set.of())).isEmpty();
        local.getPublishedVersion().setCrossSetDistractorsAllowed(true);
        assertThat(provider.prepare(room, Set.of())).hasSize(1);
        forbidden.add(new VocabularyDistractorExclusion(source, bank.get(1), "Двусмысленность"));
        assertThat(provider.prepare(room, Set.of())).isEmpty();
        assertThat(source.getStatus()).isEqualTo(EditorialStatus.PUBLISHED);
    }
    @Test void allGradesAndDirectionApplyToEveryOption() {
        var source = word(1, local, "good", "right"); var limited = word(2, local, "limited", "wrong1"); limited.setUniversalAudience(false); limited.setGradeIds(Set.of(10L));
        word(3, set(3, "kk", "ru"), "direction", "wrong2");
        for (int i = 4; i < 7; i++) word(i, local, "word" + i, "answer" + i);
        var data = distractors.prepare(List.of()); var eligible = distractors.diagnose(source, Set.of(10L, 20L), data);
        assertThat(eligible.candidates()).extracting(VocabularyDistractorService.Candidate::versionId).containsExactly(4L, 5L, 6L);
        assertThat(eligible.excludedByReason()).containsEntry("AUDIENCE", 1).containsEntry("LANGUAGE_DIRECTION", 1);
        assertThat(provider.prepare(room, Set.of(10L, 20L))).extracting(QuizRoundProvider.Source::versionId).doesNotContain(2L);
    }
    @Test void noPrimaryDuplicateTranslationOrUnverifiedCandidateCannotSupplyOptions() {
        var source = word(1, local, "good", "right"); var noPrimary = word(2, local, "bad", "wrong"); translations.removeIf(a -> a.getItemVersion() == noPrimary);
        translations.add(new VocabularyItemAnswer(noPrimary, "wrong", "wrong", false));
        word(3, local, "duplicate", "same"); word(4, local, "duplicate2", "same"); var unchecked = word(5, local, "unchecked", "wrong2"); unchecked.recordReview(null, null);
        assertThat(distractors.diagnose(source, Set.of(), distractors.prepare(List.of())).playable()).isFalse();
        assertThat(provider.prepare(room, Set.of())).isEmpty();
    }
    @Test void snapshotOrderIsDeterministicForGivenShuffleAndCorrectIdIsNotAlwaysA() {
        word(1, local, "good", "right"); for (int i = 2; i < 5; i++) word(i, local, "word" + i, "answer" + i);
        var source = provider.prepare(room, Set.of()).getFirst();
        var a = source.builder().build(1, new Random(9)); var b = source.builder().build(1, new Random(9));
        assertThat(a.getOptionsSnapshotJson()).isEqualTo(b.getOptionsSnapshotJson()); assertThat(a.getAnswerKeySnapshotJson()).isEqualTo(b.getAnswerKeySnapshotJson());
        Set<String> keys = new HashSet<>(); for (int seed = 0; seed < 50; seed++) keys.add(source.builder().build(1, new Random(seed)).getAnswerKeySnapshotJson());
        assertThat(keys.size()).isGreaterThan(1);
    }
    private VocabularyItemVersion word(long id, VocabularySet set, String prompt, String translation) {
        var item = new VocabularyItem(set); item.setStatus(EditorialStatus.PUBLISHED); id(item, id);
        var v = new VocabularyItemVersion(1, item, prompt, prompt, "Словарь"); id(v, id); v.setStatus(EditorialStatus.PUBLISHED); v.setPublishedAt(OffsetDateTime.now()); v.recordReview(1L, OffsetDateTime.now()); v.setUniversalAudience(true); v.setDifficulty(1); v.setMeaningNoteRu("Разбор"); item.setPublishedVersion(v);
        bank.add(v); translations.add(new VocabularyItemAnswer(v, translation, translation, true)); return v;
    }
    private static VocabularySet set(long id, String source, String target) {
        var set = new VocabularySet("set" + id, source, target); id(set, id); set.setStatus(EditorialStatus.PUBLISHED);
        var version = new VocabularySetVersion(1, set, "Слова", "Сөздер", "Словарь"); id(version, id); version.setStatus(EditorialStatus.PUBLISHED); version.setPublishedAt(OffsetDateTime.now()); version.recordReview(1L, OffsetDateTime.now()); set.setPublishedVersion(version); return set;
    }
    private static void id(Object entity, long id) { ReflectionTestUtils.setField(entity, "id", id); }
}
