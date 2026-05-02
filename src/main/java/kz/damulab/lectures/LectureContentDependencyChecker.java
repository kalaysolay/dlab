package kz.damulab.lectures;

import org.springframework.stereotype.Component;

import kz.damulab.content.ContentDependencyChecker;

@Component
public class LectureContentDependencyChecker implements ContentDependencyChecker {

    private final LectureVersionRepository lectureVersions;

    public LectureContentDependencyChecker(LectureVersionRepository lectureVersions) {
        this.lectureVersions = lectureVersions;
    }

    @Override
    public boolean hasTopicDependency(Long topicId) {
        return lectureVersions.existsByTopicId(topicId);
    }

    @Override
    public boolean hasSkillDependency(Long skillId) {
        return false;
    }

    @Override
    public String dependencyCode() {
        return "lectures";
    }
}
