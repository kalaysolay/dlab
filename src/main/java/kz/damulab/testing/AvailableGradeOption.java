package kz.damulab.testing;

import java.util.List;

/** Доступный класс и темы, по которым ученик может запустить тест. */
public record AvailableGradeOption(long id, String titleRu, int gradeNo, List<AvailableTopicOption> topics) {
}
