package kz.damulab.quiz.catalog;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Общая для админской диагностики и будущего движка policy безопасных слов-дистракторов. */
@Service
public class VocabularyDistractorService {
    /** Единый приоритет вариантов; равноприоритетные кандидаты можно случайно выбирать в игровом раунде. */
    public static final Comparator<Candidate> CANDIDATE_PREFERENCE = Comparator.comparing(Candidate::samePartOfSpeech, Comparator.reverseOrder())
            .thenComparingInt(Candidate::difficultyDistance).thenComparing(Candidate::sameSet, Comparator.reverseOrder());
    private final VocabularyItemVersionRepository versions;
    private final VocabularyItemAnswerRepository answers;
    private final VocabularyDistractorExclusionRepository exclusions;

    public VocabularyDistractorService(VocabularyItemVersionRepository versions,
            VocabularyItemAnswerRepository answers, VocabularyDistractorExclusionRepository exclusions) {
        this.versions = versions; this.answers = answers; this.exclusions = exclusions;
    }

    /** Диагностирует причины исключения и возвращает кандидатов в предпочтительном порядке. */
    @Transactional(readOnly = true)
    public Diagnostics diagnose(Long sourceVersionId, Set<Long> requiredGradeIds) {
        VocabularyItemVersion source = versions.findById(sourceVersionId)
                .orElseThrow(() -> new ContentValidationException("Редакция слова не найдена"));
        return diagnose(source, requiredGradeIds, prepare(List.of(source)));
    }

    /** Загружает банк, переводы и запреты один раз для пакетной диагностики нескольких игр. */
    @Transactional(readOnly = true)
    public Dataset prepare(Collection<VocabularyItemVersion> extraSources) {
        List<VocabularyItemVersion> all = versions.findPublishedCurrentVersions();
        return dataset(all, extraSources);
    }

    /** Для игрового запуска загружает только выбранный набор либо разрешённую языковую пару; policy остаётся общей. */
    @Transactional(readOnly = true)
    public Dataset prepareForGame(VocabularySet set) {
        boolean crossSet = set.getPublishedVersion() != null && set.getPublishedVersion().isCrossSetDistractorsAllowed();
        return dataset(versions.findPublishedForGame(set.getId(), set.getSourceLanguage(), set.getTargetLanguage(), crossSet), List.of());
    }
    /** Индекс текущей транзакции: проверка полноты и переводов выполняется один раз на слово. */
    public GamePool forAudience(Dataset data, Set<Long> required) {
        return new GamePool(data,required);
    }

    private Dataset dataset(List<VocabularyItemVersion> all, Collection<VocabularyItemVersion> extraSources) {
        Set<Long> ids = new HashSet<>(); extraSources.forEach(v -> ids.add(v.getId())); all.forEach(v -> ids.add(v.getId()));
        Map<Long, List<VocabularyItemAnswer>> byVersion = new HashMap<>();
        for (VocabularyItemAnswer answer : answers.findByItemVersionIdIn(ids)) {
            byVersion.computeIfAbsent(answer.getItemVersion().getId(), ignored -> new ArrayList<>()).add(answer);
        }
        return new Dataset(List.copyOf(all), byVersion, exclusions.findForVersions(ids));
    }

    /** Применяет ту же policy к подготовленному банку без дополнительных SQL-запросов. */
    public Diagnostics diagnose(VocabularyItemVersion source, Set<Long> requiredGradeIds, Dataset data) {
        Long sourceVersionId = source.getId();
        List<VocabularyItemVersion> all = data.versions();
        Map<Long, List<VocabularyItemAnswer>> byVersion = data.answers();
        Set<String> correct = normalizedAnswers(byVersion.getOrDefault(sourceVersionId, List.of()));
        Set<Long> excludedIds = new HashSet<>();
        for (VocabularyDistractorExclusion exclusion : data.exclusions()) {
            if (exclusion.getItemVersion().getId().equals(sourceVersionId)) excludedIds.add(exclusion.getExcludedItemVersion().getId());
            if (exclusion.getExcludedItemVersion().getId().equals(sourceVersionId)) excludedIds.add(exclusion.getItemVersion().getId());
        }

        Map<String, Integer> reasons = new LinkedHashMap<>(); List<Candidate> eligible = new ArrayList<>(); Set<String> usedPrimary = new HashSet<>();
        VocabularySet sourceSet = source.getItem().getSet();
        boolean crossAllowed = sourceSet.getPublishedVersion() != null && sourceSet.getPublishedVersion().isCrossSetDistractorsAllowed();
        for (VocabularyItemVersion candidate : all) {
            if (candidate.getId().equals(sourceVersionId)) { count(reasons, "SELF"); continue; }
            VocabularySet candidateSet = candidate.getItem().getSet();
            if (!QuizContentEligibility.wordSetComplete(candidateSet)) { count(reasons, "UNVERIFIED_SET"); continue; }
            if (!sameDirection(sourceSet, candidateSet)) { count(reasons, "LANGUAGE_DIRECTION"); continue; }
            boolean sameSet = sourceSet.getId().equals(candidateSet.getId());
            if (!sameSet && !crossAllowed) { count(reasons, "CROSS_SET_DISABLED"); continue; }
            if (!QuizContentEligibility.audience(candidate.isUniversalAudience(), candidate.getGradeIds(), requiredGradeIds)) { count(reasons, "AUDIENCE"); continue; }
            if (excludedIds.contains(candidate.getId())) { count(reasons, "EDITORIAL_EXCLUSION"); continue; }
            List<VocabularyItemAnswer> candidateAnswers = byVersion.getOrDefault(candidate.getId(), List.of());
            if (!QuizContentEligibility.wordComplete(candidate, candidateAnswers)) { count(reasons, "UNVERIFIED_CONTENT"); continue; }
            List<VocabularyItemAnswer> primary = candidateAnswers.stream().filter(VocabularyItemAnswer::isPrimary).toList();
            if (primary.size() != 1) { count(reasons, "PRIMARY"); continue; }
            String normalized = primary.getFirst().getAnswerNormalized();
            // Альтернатива кандидата тоже может подтвердить синонимичность пары, даже при другом primary.
            if (!Collections.disjoint(correct, normalizedAnswers(candidateAnswers))) { count(reasons, "AMBIGUOUS_TRANSLATION"); continue; }
            if (!usedPrimary.add(normalized)) { count(reasons, "DUPLICATE_DISTRACTOR"); continue; }
            eligible.add(new Candidate(candidate.getId(), primary.getFirst().getAnswerText(), sameSet,
                    Objects.equals(source.getPartOfSpeech(), candidate.getPartOfSpeech()),
                    Math.abs(source.getDifficulty() - candidate.getDifficulty())));
        }
        eligible.sort(CANDIDATE_PREFERENCE.thenComparing(Candidate::versionId));
        return new Diagnostics(List.copyOf(eligible), Collections.unmodifiableMap(reasons), eligible.size() >= 3);
    }

    /** Возвращает до limit пригодных кандидатов; при нехватке не создаёт двусмысленный игровой раунд. */
    @Transactional(readOnly = true)
    public List<Candidate> selectCandidates(Long sourceVersionId, Set<Long> requiredGradeIds, int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit должен быть положительным");
        Diagnostics result = diagnose(sourceVersionId, requiredGradeIds);
        return result.candidates().subList(0, Math.min(limit, result.candidates().size()));
    }

    /** Создаёт симметрично учитываемый запрет пары; исходные версии и история не меняются. */
    @Transactional
    public VocabularyDistractorExclusion exclude(Long sourceVersionId, Long excludedVersionId, String reason, Long actorId) {
        if (Objects.equals(sourceVersionId, excludedVersionId)) throw new ContentValidationException("Слово нельзя исключить само с собой");
        if (reason == null || reason.isBlank()) throw new ContentValidationException("Укажите причину исключения");
        VocabularyItemVersion source = versions.findById(sourceVersionId).orElseThrow(() -> new ContentValidationException("Исходная редакция не найдена"));
        VocabularyItemVersion excluded = versions.findById(excludedVersionId).orElseThrow(() -> new ContentValidationException("Исключаемая редакция не найдена"));
        VocabularyDistractorExclusion value = new VocabularyDistractorExclusion(source, excluded, reason.strip()); value.setCreatedBy(actorId);
        return exclusions.save(value);
    }

    private static Set<String> normalizedAnswers(List<VocabularyItemAnswer> values) {
        Set<String> result = new HashSet<>(); values.forEach(v -> result.add(v.getAnswerNormalized())); return result;
    }
    private static boolean sameDirection(VocabularySet a, VocabularySet b) { return a.getSourceLanguage().equals(b.getSourceLanguage()) && a.getTargetLanguage().equals(b.getTargetLanguage()); }
    private static void count(Map<String, Integer> reasons, String code) { reasons.merge(code, 1, Integer::sum); }

    /** Безопасный вариант для кнопки ответа будущего движка. */
    public record Candidate(Long versionId, String answerText, boolean sameSet, boolean samePartOfSpeech, int difficultyDistance) {}
    /** Результат диагностики с точными причинами и отдельным признаком наличия трёх вариантов. */
    public record Diagnostics(List<Candidate> candidates, Map<String, Integer> excludedByReason, boolean playable) {}
    /** Согласованный пакет данных только для чтения внутри диагностической транзакции. */
    public record Dataset(List<VocabularyItemVersion> versions, Map<Long, List<VocabularyItemAnswer>> answers,
                          List<VocabularyDistractorExclusion> exclusions) {}

    /** Общая подготовка игровой аудитории без кеша между транзакциями и без устаревших публикаций. */
    public static final class GamePool {
        private final List<Word> eligible=new ArrayList<>();
        private final Map<Long,Word> words=new HashMap<>();
        private final Map<Long,Set<Long>> excluded=new HashMap<>();
        private final Map<String,Set<Long>> byTranslation=new HashMap<>();
        private GamePool(Dataset data,Set<Long> required) {
            for(var v:data.versions()) {
                var translations=data.answers().getOrDefault(v.getId(),List.of());
                var primary=translations.stream().filter(VocabularyItemAnswer::isPrimary).findFirst().orElse(null);
                var word=new Word(v,primary,Set.copyOf(normalizedAnswers(translations)));
                words.put(v.getId(),word);
                if(QuizContentEligibility.wordSetComplete(v.getItem().getSet()) && QuizContentEligibility.wordComplete(v,translations)
                        && QuizContentEligibility.audience(v.isUniversalAudience(),v.getGradeIds(),required)) {
                    eligible.add(word);
                    for(String translation:word.translations()) byTranslation.computeIfAbsent(translation,k->new HashSet<>()).add(v.getId());
                }
            }
            // Запрет симметричен: его направление в редакторе не меняет игровой подбор.
            for(var pair:data.exclusions()) {
                long a=pair.getItemVersion().getId(),b=pair.getExcludedItemVersion().getId();
                excluded.computeIfAbsent(a,k->new HashSet<>()).add(b);
                excluded.computeIfAbsent(b,k->new HashSet<>()).add(a);
            }
        }
        /** Пригодные источники выбранного набора в прежнем порядке банка. */
        public List<VocabularyItemVersion> sources(Long setId) {
            return eligible.stream().filter(w->w.version().getItem().getSet().getId().equals(setId)).map(Word::version).toList();
        }
        /** Проверка останавливается на трёх вариантах; полный список нужен только выбранным раундам. */
        public boolean playable(VocabularyItemVersion source) { return candidates(source,3).size()==3; }
        /** Полный выбор сохраняет приоритеты и случайный выбор среди равных в провайдере. */
        public List<Candidate> candidates(VocabularyItemVersion source) { return candidates(source,Integer.MAX_VALUE); }
        private List<Candidate> candidates(VocabularyItemVersion source,int limit) {
            var own=words.get(source.getId()); if(own==null) return List.of();
            var sourceSet=source.getItem().getSet();
            boolean cross=sourceSet.getPublishedVersion()!=null && sourceSet.getPublishedVersion().isCrossSetDistractorsAllowed();
            Set<Long> forbidden=new HashSet<>(excluded.getOrDefault(source.getId(),Set.of())); forbidden.add(source.getId());
            // Индекс альтернатив обоих слов предотвращает неоднозначные переводы без повторного разбора банка.
            for(String translation:own.translations()) forbidden.addAll(byTranslation.getOrDefault(translation,Set.of()));
            Set<String> used=new HashSet<>(); List<Candidate> result=new ArrayList<>();
            for(var word:eligible) {
                var candidate=word.version(); var set=candidate.getItem().getSet();
                boolean sameSet=sourceSet.getId().equals(set.getId());
                if(forbidden.contains(candidate.getId()) || !sameDirection(sourceSet,set) || (!sameSet && !cross)
                        || !used.add(word.primary().getAnswerNormalized())) continue;
                result.add(new Candidate(candidate.getId(),word.primary().getAnswerText(),sameSet,
                        Objects.equals(source.getPartOfSpeech(),candidate.getPartOfSpeech()),Math.abs(source.getDifficulty()-candidate.getDifficulty())));
                if(result.size()==limit) break;
            }
            return result;
        }
        private record Word(VocabularyItemVersion version,VocabularyItemAnswer primary,Set<String> translations) {}
    }
}
