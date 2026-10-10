package kz.damulab.quiz.catalog;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Доступ к QuizRoomCreationKey; публикация и архивирование выполняются сервисами, не CRUD сам по себе. */
public interface QuizRoomCreationKeyRepository extends JpaRepository<QuizRoomCreationKey, Long> {
    /** Находит повтор create, загружая комнату в том же запросе. */
    @EntityGraph(attributePaths = "room")
    Optional<QuizRoomCreationKey> findByStudentProfileIdAndKeyHash(Long studentProfileId, String keyHash);
}
