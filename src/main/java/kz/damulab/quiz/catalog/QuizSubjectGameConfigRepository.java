package kz.damulab.quiz.catalog;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Доступ к QuizSubjectGameConfig; публикация и архивирование выполняются сервисами, не CRUD сам по себе. */
public interface QuizSubjectGameConfigRepository extends JpaRepository<QuizSubjectGameConfig, Long> {
    /** Загружает источники сразу для набора карточек, не отдельным запросом на карточку. */
    @EntityGraph(attributePaths = {"subject", "questionTypes"})
    List<QuizSubjectGameConfig> findByGameVersionIdIn(Collection<Long> ids);
}
