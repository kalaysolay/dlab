package kz.damulab.quiz;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizResultRepository extends JpaRepository<QuizResult, Long> {

    List<QuizResult> findByRoomIdOrderByScoreDescPercentDescCreatedAtAsc(Long roomId);

    Optional<QuizResult> findByRoomIdAndParticipantId(Long roomId, Long participantId);

    boolean existsByRoomId(Long roomId);
}
