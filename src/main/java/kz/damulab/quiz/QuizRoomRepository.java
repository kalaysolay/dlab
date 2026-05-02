package kz.damulab.quiz;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRoomRepository extends JpaRepository<QuizRoom, Long> {

    Optional<QuizRoom> findByCodeIgnoreCase(String code);

    java.util.List<QuizRoom> findByStatus(QuizRoomStatus status);

    boolean existsByCodeIgnoreCase(String code);
}
