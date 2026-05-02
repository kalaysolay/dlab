package kz.damulab.quiz;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRoundRepository extends JpaRepository<QuizRound, Long> {

    List<QuizRound> findByRoomIdOrderByOrderNoAsc(Long roomId);

    Optional<QuizRound> findByIdAndRoomId(Long id, Long roomId);

    long countByRoomId(Long roomId);
}
