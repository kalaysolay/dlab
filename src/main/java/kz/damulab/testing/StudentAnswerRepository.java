package kz.damulab.testing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentAnswerRepository extends JpaRepository<StudentAnswer, Long> {

    Optional<StudentAnswer> findBySessionQuestionId(Long sessionQuestionId);

    List<StudentAnswer> findBySessionQuestionSessionId(Long sessionId);
}
