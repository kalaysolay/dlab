package kz.damulab.content;

public interface ContentDependencyChecker {

    boolean hasTopicDependency(Long topicId);

    boolean hasSkillDependency(Long skillId);

    String dependencyCode();
}
