package kz.damulab.testing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnswerEvaluationRepository extends JpaRepository<AnswerEvaluation, Long> {

    Optional<AnswerEvaluation> findBySessionQuestionId(Long sessionQuestionId);

    List<AnswerEvaluation> findBySessionQuestionSessionId(Long sessionId);

    List<AnswerEvaluation> findBySessionQuestionSessionIdInOrderByEvaluatedAtDesc(List<Long> sessionIds);

    @Query("""
            select distinct evaluation.sessionQuestion.questionVersion.id
            from AnswerEvaluation evaluation
            where evaluation.sessionQuestion.session.studentProfile.id = :studentProfileId
            """)
    List<Long> findAttemptedQuestionVersionIds(@Param("studentProfileId") Long studentProfileId);

    List<AnswerEvaluation> findTop5BySessionQuestionSessionStudentProfileIdAndCorrectFalseOrderByEvaluatedAtDesc(Long studentProfileId);
}
