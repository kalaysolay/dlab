package kz.damulab.gamification;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentAchievementRepository extends JpaRepository<StudentAchievement, Long> {

    boolean existsByStudentProfileIdAndAchievementId(Long studentProfileId, Long achievementId);

    @EntityGraph(attributePaths = "achievement")
    List<StudentAchievement> findByStudentProfileIdOrderByEarnedAtDesc(Long studentProfileId);
}
