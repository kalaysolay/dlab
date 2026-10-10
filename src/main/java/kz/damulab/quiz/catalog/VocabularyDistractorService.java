package kz.damulab.quiz.catalog;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Общая для админской диагностики и будущего движка policy безопасных слов-дистракторов. */
@Service
public class VocabularyDistractorService {
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
        eligible.sort(Comparator.comparing(Candidate::samePartOfSpeech, Comparator.reverseOrder())
                .thenComparingInt(Candidate::difficultyDistance)
                .thenComparing(Candidate::sameSet, Comparator.reverseOrder())
                .thenComparing(Candidate::versionId));
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
}
