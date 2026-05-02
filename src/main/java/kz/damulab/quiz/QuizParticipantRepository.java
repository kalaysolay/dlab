package kz.damulab.quiz;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizParticipantRepository extends JpaRepository<QuizParticipant, Long> {

    List<QuizParticipant> findByRoomIdOrderByJoinedAtAscIdAsc(Long roomId);

    Optional<QuizParticipant> findByRoomIdAndStudentProfileId(Long roomId, Long studentProfileId);

    long countByRoomId(Long roomId);
}
