package kz.damulab.questions;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionFlagRepository extends JpaRepository<QuestionFlag, Long> {

    List<QuestionFlag> findByStatus(QuestionFlagStatus status);

    List<QuestionFlag> findByQuestionIdOrderByCreatedAtDesc(Long questionId);
}
