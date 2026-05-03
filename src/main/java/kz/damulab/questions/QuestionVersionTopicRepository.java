package kz.damulab.questions;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionVersionTopicRepository extends JpaRepository<QuestionVersionTopic, Long> {

    boolean existsByTopicId(Long topicId);
}
