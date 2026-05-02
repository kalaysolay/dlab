package kz.damulab.analytics;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillMasteryRepository extends JpaRepository<SkillMastery, Long> {

    List<SkillMastery> findByStudentProfileIdOrderByMasteryPercentAscUpdatedAtDesc(Long studentProfileId);

    Optional<SkillMastery> findFirstByStudentProfileIdAndTopicIdAndAtomicSkillIsNull(Long studentProfileId, Long topicId);

    Optional<SkillMastery> findFirstByStudentProfileIdAndTopicIdAndAtomicSkillId(Long studentProfileId, Long topicId, Long atomicSkillId);
}
