package kz.damulab.questions;

import org.springframework.stereotype.Component;

import kz.damulab.content.ContentDependencyChecker;

@Component
public class QuestionContentDependencyChecker implements ContentDependencyChecker {

    private final QuestionVersionRepository questionVersions;
    private final QuestionVersionTopicRepository versionTopics;

    public QuestionContentDependencyChecker(
            QuestionVersionRepository questionVersions,
            QuestionVersionTopicRepository versionTopics
    ) {
        this.questionVersions = questionVersions;
        this.versionTopics = versionTopics;
    }

    @Override
    public boolean hasTopicDependency(Long topicId) {
        return versionTopics.existsByTopicId(topicId);
    }

    @Override
    public boolean hasSkillDependency(Long skillId) {
        return questionVersions.existsByAtomicSkillId(skillId);
    }

    @Override
    public String dependencyCode() {
        return "questions";
    }
}
