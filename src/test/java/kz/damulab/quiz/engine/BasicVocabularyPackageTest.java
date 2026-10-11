package kz.damulab.quiz.engine;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

/** Проверяет реальные файлы банка и четыре варианта через общий provider; в БД ничего не публикует. */
class BasicVocabularyPackageTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final QuizTextNormalizer normalizer = new QuizTextNormalizer();

    @ParameterizedTest(name = "{0}: {1} слов, {2} → {3}")
    @CsvSource({"basic-ru-en-300-v1,300,ru,en", "basic-en-ru-400-v1,400,en,ru", "basic-ru-kk-300-v1,300,ru,kk"})
    void everyWordFormsFourDistinctOptionsWithOneKey(String code, int count, String source, String target) throws Exception {
        JsonNode data;
        try (var stream = new ClassPathResource("quiz/basic-vocabulary/" + code + ".json").getInputStream()) {
            data = mapper.readTree(stream);
        }
        assertThat(data.path("status").asText()).isEqualTo("DRAFT");
        assertThat(data.path("audiencePolicy").asText()).isEqualTo("UNIVERSAL");
        assertThat(data.path("crossSetDistractorsAllowed").asBoolean()).isFalse();
        assertThat(data.path("sourceLanguage").asText()).isEqualTo(source);
        assertThat(data.path("targetLanguage").asText()).isEqualTo(target);
        assertThat(data.path("items").size()).isEqualTo(count);

        var versions = mock(VocabularyItemVersionRepository.class);
        var answers = mock(VocabularyItemAnswerRepository.class);
        var exclusions = mock(VocabularyDistractorExclusionRepository.class);
        var configs = mock(QuizWordGameConfigRepository.class);
        var distractors = new VocabularyDistractorService(versions, answers, exclusions);
        var provider = new VocabularyRoundProvider(configs, distractors, new QuizSnapshotJson(mapper));
        var set = new VocabularySet(code, source, target); id(set, 1);
        var published = new VocabularySetVersion(1, set, data.path("titleRu").asText(), data.path("titleKk").asText(), "Test fixture only");
        id(published, 1); published.setStatus(EditorialStatus.PUBLISHED); published.setPublishedAt(OffsetDateTime.now());
        published.recordReview(1L, OffsetDateTime.now()); set.setPublishedVersion(published); set.setStatus(EditorialStatus.PUBLISHED);
        var bank = new ArrayList<VocabularyItemVersion>();
        var translations = new ArrayList<VocabularyItemAnswer>();
        var sources = new HashSet<String>();
        Map<Long, String> primaryById = new HashMap<>();
        for (JsonNode row : data.path("items")) {
            long id = bank.size() + 1L;
            String prompt = row.path("sourceText").asText(), primary = row.path("primaryAnswer").asText();
            assertThat(prompt).isNotBlank(); assertThat(primary).isNotBlank();
            assertThat(sources.add(normalizer.normalize(prompt))).as("Повтор исходного слова: %s", prompt).isTrue();
            assertThat(row.path("universalAudience").asBoolean()).isTrue();
            assertThat(row.path("gradeIds").isEmpty()).isTrue();
            var item = new VocabularyItem(set); id(item, id); item.setStatus(EditorialStatus.PUBLISHED);
            // Публикация только несохранённых test-объектов моделирует будущую проверку редактором.
            // Это проверка движка на данных пакета, а не утверждение о лингвистической приёмке черновика.
            var version = new VocabularyItemVersion(1, item, prompt, normalizer.normalize(prompt), "Test fixture only");
            id(version, id); version.setStatus(EditorialStatus.PUBLISHED); version.setPublishedAt(OffsetDateTime.now());
            version.recordReview(1L, OffsetDateTime.now()); version.setUniversalAudience(true);
            version.setPartOfSpeech(row.path("partOfSpeech").asText()); version.setDifficulty(row.path("difficulty").asInt());
            version.setMeaningNoteRu(row.path("meaningNoteRu").asText()); item.setPublishedVersion(version);
            bank.add(version); primaryById.put(id, primary);
            translations.add(new VocabularyItemAnswer(version, primary, normalizer.normalize(primary), true));
            var unique = new HashSet<String>(); unique.add(normalizer.normalize(primary));
            for (JsonNode alternative : row.path("alternativeAnswers")) {
                assertThat(alternative.asText()).isNotBlank();
                assertThat(unique.add(normalizer.normalize(alternative.asText()))).as("Повтор перевода: %s", prompt).isTrue();
                translations.add(new VocabularyItemAnswer(version, alternative.asText(), normalizer.normalize(alternative.asText()), false));
            }
        }
        when(versions.findPublishedForGame(any(), any(), any(), anyBoolean())).thenReturn(bank);
        when(answers.findByItemVersionIdIn(any())).thenReturn(translations);
        when(exclusions.findForVersions(any())).thenReturn(List.of());
        var gameVersion = new QuizGameVersion(1, null, QuizGameType.WORD_TRANSLATION, "Слова", "Сөздер"); id(gameVersion, 1);
        var room = new QuizRoom("QZABCD", null, null, null, "ru", null, 10, 20, 4);
        room.attachGameVersion(gameVersion, OffsetDateTime.now().plusMinutes(30));
        when(configs.findById(1L)).thenReturn(Optional.of(new QuizWordGameConfig(gameVersion, set, source, target)));
        var prepared = provider.prepare(room, Set.of());
        assertThat(prepared).as("Слова без трёх однозначных дистракторов").hasSize(count);
        for (var preparedSource : prepared) {
            var round = preparedSource.builder().build(1, new Random(preparedSource.versionId()));
            JsonNode options = mapper.readTree(round.getOptionsSnapshotJson());
            String key = mapper.readTree(round.getAnswerKeySnapshotJson()).asText();
            assertThat(options.size()).isEqualTo(4);
            var texts = new HashSet<String>(); int keys = 0;
            for (JsonNode option : options) {
                assertThat(texts.add(normalizer.normalize(option.path("text").asText()))).isTrue();
                if (option.path("id").asText().equals(key)) {
                    keys++; assertThat(option.path("text").asText()).isEqualTo(primaryById.get(preparedSource.versionId()));
                }
            }
            assertThat(keys).isEqualTo(1);
        }
        // Большой банк должен давать разные дистракторы, а не только разные позиции первых трёх слов.
        var first=prepared.getFirst(); var observed=new HashSet<String>();
        for(int seed=0;seed<20;seed++) {
            var round=first.builder().build(1,new Random(seed));
            for(JsonNode option:mapper.readTree(round.getOptionsSnapshotJson())) {
                String text=option.path("text").asText();
                if(!text.equals(primaryById.get(first.versionId()))) observed.add(text);
            }
        }
        assertThat(observed.size()).isGreaterThan(3);
    }

    private static void id(Object value, long id) { ReflectionTestUtils.setField(value, "id", id); }
}
