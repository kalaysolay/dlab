package kz.damulab.testing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestSessionRepository extends JpaRepository<TestSession, Long> {

    Optional<TestSession> findByIdAndStudentProfileId(Long id, Long studentProfileId);

    List<TestSession> findTop10ByStudentProfileIdOrderByStartedAtDesc(Long studentProfileId);
}
