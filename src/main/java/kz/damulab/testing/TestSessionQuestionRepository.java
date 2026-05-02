package kz.damulab.testing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestSessionQuestionRepository extends JpaRepository<TestSessionQuestion, Long> {

    List<TestSessionQuestion> findBySessionIdOrderByOrderNoAsc(Long sessionId);

    Optional<TestSessionQuestion> findByIdAndSessionId(Long id, Long sessionId);
}
