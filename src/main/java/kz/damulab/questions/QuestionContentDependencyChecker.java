package kz.damulab.questions;

import org.springframework.stereotype.Component;

import kz.damulab.content.ContentDependencyChecker;

@Component
public class QuestionContentDependencyChecker implements ContentDependencyChecker {

    private final QuestionVersionRepository questionVersions;

    public QuestionContentDependencyChecker(QuestionVersionRepository questionVersions) {
        this.questionVersions = questionVersions;
    }

    @Override
    public boolean hasTopicDependency(Long topicId) {
        return questionVersions.existsByTopicId(topicId);
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
