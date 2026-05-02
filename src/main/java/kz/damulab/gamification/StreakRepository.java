package kz.damulab.gamification;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StreakRepository extends JpaRepository<Streak, Long> {

    Optional<Streak> findByStudentProfileId(Long studentProfileId);
}
