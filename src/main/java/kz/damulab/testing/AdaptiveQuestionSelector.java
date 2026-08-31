package kz.damulab.testing;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import kz.damulab.analytics.SkillMastery;
import kz.damulab.config.DamulabTestingProperties;
import kz.damulab.config.QuestionSelectionStrategy;
import kz.damulab.questions.QuestionVersion;

/**
 * Формирует учебный тест взвешенной случайной выборкой без повторов.
 *
 * <p>Слабые навыки получают больший вес, но сильные и неизученные вопросы остаются в пуле.
 * Ограничение доли темы сохраняет разнообразие; при маленьком банке оно мягко снимается,
 * чтобы сессия всё равно получила требуемое количество вопросов.</p>
 */
@Component
public class AdaptiveQuestionSelector {

    private static final double UNKNOWN_MASTERY_WEIGHT = 60.0;
    private static final double MIN_MASTERY_WEIGHT = 10.0;
    private static final double NEVER_ATTEMPTED_BONUS = 15.0;
    private static final double RECENT_MISTAKE_BONUS = 30.0;
    private static final double STALE_MASTERY_BONUS = 20.0;
    private static final double RECENT_REPEAT_FACTOR = 0.2;

    private final DamulabTestingProperties properties;
    private final Clock clock;
    private final RandomGenerator random;

    @Autowired
    public AdaptiveQuestionSelector(DamulabTestingProperties properties, Clock clock) {
        this(properties, clock, RandomGenerator.getDefault());
    }

    AdaptiveQuestionSelector(DamulabTestingProperties properties, Clock clock, RandomGenerator random) {
        this.properties = properties;
        this.clock = clock;
        this.random = random;
    }

    /**
     * Выбирает вопросы с учётом mastery, истории попыток и ограничения разнообразия тем.
     *
     * @param candidates уже отфильтрованные опубликованные версии предмета/класса/сложности
     * @param targetCount требуемый размер сессии
     * @param masteries текущие показатели ученика по темам и атомарным навыкам
     * @param attemptedVersionIds версии, которые ученик когда-либо завершал в тестах
     * @param recentVersionIds версии из последних сессий, которые не стоит сразу повторять
     * @param latestCorrectByVersion последний результат версии в недавнем окне
     * @param strategy режим подбора
     */
    public QuestionSelectionResult select(
            List<QuestionVersion> candidates,
            int targetCount,
            List<SkillMastery> masteries,
            Set<Long> attemptedVersionIds,
            Set<Long> recentVersionIds,
            Map<Long, Boolean> latestCorrectByVersion,
            QuestionSelectionStrategy strategy
    ) {
        Map<Long, QuestionVersion> uniqueById = new LinkedHashMap<>();
        candidates.forEach(version -> uniqueById.putIfAbsent(version.getId(), version));
        List<QuestionVersion> uniqueCandidates = new ArrayList<>(uniqueById.values());
        int target = Math.min(Math.max(targetCount, 0), uniqueCandidates.size());
        QuestionSelectionStrategy effectiveStrategy = strategy == null ? QuestionSelectionStrategy.ADAPTIVE : strategy;
        MasteryIndex masteryIndex = new MasteryIndex(masteries);

        List<Candidate> remaining = uniqueCandidates.stream()
                .map(version -> candidate(
                        version,
                        masteryIndex,
                        attemptedVersionIds,
                        recentVersionIds,
                        latestCorrectByVersion,
                        effectiveStrategy
                ))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<Candidate> selected = new ArrayList<>(target);
        Map<Long, Integer> selectedByTopic = new HashMap<>();
        int topicLimit = topicLimit(target);

        while (selected.size() < target) {
            List<Candidate> eligible = remaining.stream()
                    .filter(candidate -> withinTopicLimit(candidate, selectedByTopic, topicLimit))
                    .toList();
            // Маленький или однородный банк важнее мягкой квоты: снимаем её только когда иначе не заполнить тест.
            if (eligible.isEmpty()) {
                eligible = List.copyOf(remaining);
            }
            Candidate picked = weightedPick(eligible);
            selected.add(picked);
            remaining.remove(picked);
            if (picked.topicId() != null) {
                selectedByTopic.merge(picked.topicId(), 1, Integer::sum);
            }
        }

        int weak = 0;
        int watch = 0;
        int unseen = 0;
        int strong = 0;
        for (Candidate candidate : selected) {
            switch (candidate.band()) {
                case WEAK -> weak++;
                case WATCH -> watch++;
                case UNSEEN -> unseen++;
                case STRONG -> strong++;
            }
        }
        return new QuestionSelectionResult(
                selected.stream().map(Candidate::version).toList(),
                effectiveStrategy,
                weak,
                watch,
                unseen,
                strong
        );
    }

    private Candidate candidate(
            QuestionVersion version,
            MasteryIndex masteryIndex,
            Set<Long> attemptedVersionIds,
            Set<Long> recentVersionIds,
            Map<Long, Boolean> latestCorrectByVersion,
            QuestionSelectionStrategy strategy
    ) {
        SkillMastery mastery = masteryIndex.forQuestion(version);
        MasteryBand band = band(mastery);
        double weight = strategy == QuestionSelectionStrategy.RANDOM
                ? 1.0
                : adaptiveWeight(version, mastery, attemptedVersionIds, recentVersionIds, latestCorrectByVersion);
        Long topicId = version.getPrimaryTopic() == null ? null : version.getPrimaryTopic().getId();
        return new Candidate(version, topicId, band, weight);
    }

    double adaptiveWeight(
            QuestionVersion version,
            SkillMastery mastery,
            Set<Long> attemptedVersionIds,
            Set<Long> recentVersionIds,
            Map<Long, Boolean> latestCorrectByVersion
    ) {
        double weight = mastery == null
                ? UNKNOWN_MASTERY_WEIGHT
                : Math.max(MIN_MASTERY_WEIGHT, 100.0 - mastery.getMasteryPercent().doubleValue());
        if (!attemptedVersionIds.contains(version.getId())) {
            weight += NEVER_ATTEMPTED_BONUS;
        }
        if (Boolean.FALSE.equals(latestCorrectByVersion.get(version.getId()))) {
            weight += RECENT_MISTAKE_BONUS;
        }
        if (isStale(mastery)) {
            weight += STALE_MASTERY_BONUS;
        }
        if (recentVersionIds.contains(version.getId())) {
            weight *= RECENT_REPEAT_FACTOR;
        }
        return Math.max(1.0, weight);
    }

    private boolean isStale(SkillMastery mastery) {
        int staleDays = properties.getMasteryStaleAfterDays();
        if (mastery == null || mastery.getLastAttemptAt() == null || staleDays <= 0) {
            return false;
        }
        return mastery.getLastAttemptAt().isBefore(OffsetDateTime.now(clock).minusDays(staleDays));
    }

    private int topicLimit(int targetCount) {
        int percent = Math.max(1, Math.min(100, properties.getMaxTopicSharePercent()));
        return Math.max(1, (int) Math.ceil(targetCount * percent / 100.0));
    }

    private boolean withinTopicLimit(Candidate candidate, Map<Long, Integer> selectedByTopic, int topicLimit) {
        return candidate.topicId() == null || selectedByTopic.getOrDefault(candidate.topicId(), 0) < topicLimit;
    }

    private Candidate weightedPick(List<Candidate> candidates) {
        double totalWeight = candidates.stream().mapToDouble(Candidate::weight).sum();
        double cursor = random.nextDouble(totalWeight);
        for (Candidate candidate : candidates) {
            cursor -= candidate.weight();
            if (cursor < 0) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private MasteryBand band(SkillMastery mastery) {
        if (mastery == null) {
            return MasteryBand.UNSEEN;
        }
        double percent = mastery.getMasteryPercent().doubleValue();
        if (percent < 50) {
            return MasteryBand.WEAK;
        }
        if (percent < 75) {
            return MasteryBand.WATCH;
        }
        return MasteryBand.STRONG;
    }

    private enum MasteryBand {
        WEAK,
        WATCH,
        UNSEEN,
        STRONG
    }

    private record Candidate(QuestionVersion version, Long topicId, MasteryBand band, double weight) {
    }

    private static final class MasteryIndex {
        private final Map<Long, SkillMastery> byTopic = new HashMap<>();
        private final Map<Long, SkillMastery> bySkill = new HashMap<>();

        private MasteryIndex(List<SkillMastery> masteries) {
            for (SkillMastery mastery : masteries) {
                if (mastery.getAtomicSkill() == null) {
                    byTopic.put(mastery.getTopic().getId(), mastery);
                } else {
                    bySkill.put(mastery.getAtomicSkill().getId(), mastery);
                }
            }
        }

        private SkillMastery forQuestion(QuestionVersion version) {
            if (version.getAtomicSkill() != null) {
                SkillMastery skillMastery = bySkill.get(version.getAtomicSkill().getId());
                if (skillMastery != null) {
                    return skillMastery;
                }
            }
            return version.getPrimaryTopic() == null ? null : byTopic.get(version.getPrimaryTopic().getId());
        }
    }
}
