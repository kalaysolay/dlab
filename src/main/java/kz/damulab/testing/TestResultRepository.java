package kz.damulab.testing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    Optional<TestResult> findBySessionId(Long sessionId);

    List<TestResult> findTop10BySessionStudentProfileIdOrderByCreatedAtDesc(Long studentProfileId);

    long countBySessionStudentProfileId(Long studentProfileId);
}
