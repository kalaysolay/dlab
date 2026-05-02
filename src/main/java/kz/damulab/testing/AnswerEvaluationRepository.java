package kz.damulab.testing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerEvaluationRepository extends JpaRepository<AnswerEvaluation, Long> {

    Optional<AnswerEvaluation> findBySessionQuestionId(Long sessionQuestionId);

    List<AnswerEvaluation> findBySessionQuestionSessionId(Long sessionId);

    List<AnswerEvaluation> findTop5BySessionQuestionSessionStudentProfileIdAndCorrectFalseOrderByEvaluatedAtDesc(Long studentProfileId);
}
