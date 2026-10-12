package kz.damulab.quiz.catalog;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Доступ к QuizFactGameConfig; публикация и архивирование выполняются сервисами, не CRUD сам по себе. */
public interface QuizFactGameConfigRepository extends JpaRepository<QuizFactGameConfig, Long> {
    /** Загружает источники сразу для набора карточек, не отдельным запросом на карточку. */
    @EntityGraph(attributePaths = "factSet")
    List<QuizFactGameConfig> findByGameVersionIdIn(Collection<Long> ids);
}
