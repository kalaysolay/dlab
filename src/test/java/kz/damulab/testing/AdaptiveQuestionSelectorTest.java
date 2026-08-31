package kz.damulab.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kz.damulab.analytics.SkillMastery;
import kz.damulab.config.DamulabTestingProperties;
import kz.damulab.config.QuestionSelectionStrategy;
import kz.damulab.content.AtomicSkill;
import kz.damulab.content.Topic;
import kz.damulab.questions.QuestionVersion;

class AdaptiveQuestionSelectorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void adaptiveSelectionPrefersWeakSkillsButKeepsStrongQuestionsPossible() {
        DamulabTestingProperties properties = properties(100);
        AdaptiveQuestionSelector selector = new AdaptiveQuestionSelector(properties, CLOCK, new Random(42));
        List<QuestionVersion> candidates = new ArrayList<>();
        List<SkillMastery> masteries = new ArrayList<>();
        Set<Long> attempted = new HashSet<>();

        for (int index = 1; index <= 40; index++) {
            boolean weak = index <= 20;
            Topic topic = topic((long) index);
            AtomicSkill skill = skill((long) index, topic);
            QuestionVersion version = question((long) index, topic, skill);
            candidates.add(version);
            masteries.add(mastery(topic, skill, weak ? 10 : 95, OffsetDateTime.now(CLOCK)));
            attempted.add(version.getId());
        }

        QuestionSelectionResult result = selector.select(
                candidates,
                12,
                masteries,
                attempted,
                Set.of(),
                Map.of(),
                QuestionSelectionStrategy.ADAPTIVE
        );

        assertThat(result.questions()).hasSize(12).doesNotHaveDuplicates();
        assertThat(result.weakQuestions()).isGreaterThan(result.strongQuestions());
        assertThat(result.strongQuestions()).isGreaterThan(0);
    }

    @Test
    void recentQuestionGetsStrongRepeatPenaltyEvenAfterMistake() {
        AdaptiveQuestionSelector selector = new AdaptiveQuestionSelector(properties(100), CLOCK, new Random(1));
        Topic topic = topic(1L);
        AtomicSkill skill = skill(11L, topic);
        QuestionVersion version = question(101L, topic, skill);
        SkillMastery mastery = mastery(topic, skill, 20, OffsetDateTime.now(CLOCK));

        double ordinaryWeight = selector.adaptiveWeight(version, mastery, Set.of(101L), Set.of(), Map.of(101L, false));
        double recentWeight = selector.adaptiveWeight(version, mastery, Set.of(101L), Set.of(101L), Map.of(101L, false));

        assertThat(ordinaryWeight).isEqualTo(110.0);
        assertThat(recentWeight).isEqualTo(22.0);
    }

    @Test
    void staleMasteryAndNeverAttemptedQuestionReceiveExplorationBonuses() {
        AdaptiveQuestionSelector selector = new AdaptiveQuestionSelector(properties(100), CLOCK, new Random(1));
        Topic topic = topic(1L);
        AtomicSkill skill = skill(11L, topic);
        QuestionVersion version = question(101L, topic, skill);
        SkillMastery staleMastery = mastery(topic, skill, 90, OffsetDateTime.now(CLOCK).minusDays(45));

        double weight = selector.adaptiveWeight(version, staleMastery, Set.of(), Set.of(), Map.of());

        assertThat(weight).isEqualTo(45.0);
    }

    @Test
    void topicShareIsLimitedAndRelaxedOnlyWhenBankHasNoAlternative() {
        AdaptiveQuestionSelector selector = new AdaptiveQuestionSelector(properties(40), CLOCK, new Random(7));
        Topic dominant = topic(1L);
        Topic alternative = topic(2L);
        List<QuestionVersion> candidates = new ArrayList<>();
        for (long id = 1; id <= 8; id++) {
            candidates.add(question(id, dominant, null));
        }
        candidates.add(question(9L, alternative, null));
        candidates.add(question(10L, alternative, null));

        QuestionSelectionResult result = selector.select(
                candidates,
                5,
                List.of(),
                Set.of(),
                Set.of(),
                Map.of(),
                QuestionSelectionStrategy.RANDOM
        );

        long dominantCount = result.questions().stream()
                .filter(question -> question.getPrimaryTopic().getId().equals(1L))
                .count();
        assertThat(dominantCount).isEqualTo(3);
        assertThat(result.questions()).hasSize(5).doesNotHaveDuplicates();
    }

    @Test
    void smallSingleTopicBankStillFillsRequestedSession() {
        AdaptiveQuestionSelector selector = new AdaptiveQuestionSelector(properties(40), CLOCK, new Random(9));
        Topic onlyTopic = topic(1L);
        List<QuestionVersion> candidates = List.of(
                question(1L, onlyTopic, null),
                question(2L, onlyTopic, null),
                question(3L, onlyTopic, null)
        );

        QuestionSelectionResult result = selector.select(
                candidates,
                3,
                List.of(),
                Set.of(),
                Set.of(),
                Map.of(),
                QuestionSelectionStrategy.ADAPTIVE
        );

        assertThat(result.questions()).hasSize(3).doesNotHaveDuplicates();
        assertThat(result.unseenQuestions()).isEqualTo(3);
    }

    private DamulabTestingProperties properties(int maxTopicSharePercent) {
        DamulabTestingProperties properties = new DamulabTestingProperties();
        properties.setMaxTopicSharePercent(maxTopicSharePercent);
        properties.setMasteryStaleAfterDays(30);
        return properties;
    }

    private Topic topic(Long id) {
        Topic topic = mock(Topic.class);
        when(topic.getId()).thenReturn(id);
        return topic;
    }

    private AtomicSkill skill(Long id, Topic topic) {
        AtomicSkill skill = mock(AtomicSkill.class);
        when(skill.getId()).thenReturn(id);
        when(skill.getTopic()).thenReturn(topic);
        return skill;
    }

    private QuestionVersion question(Long id, Topic topic, AtomicSkill skill) {
        QuestionVersion version = mock(QuestionVersion.class);
        when(version.getId()).thenReturn(id);
        when(version.getPrimaryTopic()).thenReturn(topic);
        when(version.getAtomicSkill()).thenReturn(skill);
        return version;
    }

    private SkillMastery mastery(
            Topic topic,
            AtomicSkill skill,
            int percent,
            OffsetDateTime lastAttemptAt
    ) {
        SkillMastery mastery = mock(SkillMastery.class);
        when(mastery.getTopic()).thenReturn(topic);
        when(mastery.getAtomicSkill()).thenReturn(skill);
        when(mastery.getMasteryPercent()).thenReturn(BigDecimal.valueOf(percent));
        when(mastery.getLastAttemptAt()).thenReturn(lastAttemptAt);
        return mastery;
    }
}
