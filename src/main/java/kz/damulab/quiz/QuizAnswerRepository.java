package kz.damulab.quiz;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {

    Optional<QuizAnswer> findByRoundIdAndParticipantId(Long roundId, Long participantId);

    List<QuizAnswer> findByParticipantId(Long participantId);

    List<QuizAnswer> findByParticipantIdAndRoundRoomId(Long participantId, Long roomId);

    List<QuizAnswer> findByRoundRoomId(Long roomId);

    long countByRoundRoomId(Long roomId);
}
