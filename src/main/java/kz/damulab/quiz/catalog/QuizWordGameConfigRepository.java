package kz.damulab.quiz.catalog;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Доступ к QuizWordGameConfig; публикация и архивирование выполняются сервисами, не CRUD сам по себе. */
public interface QuizWordGameConfigRepository extends JpaRepository<QuizWordGameConfig, Long> {
    /** Загружает источники сразу для набора карточек, не отдельным запросом на карточку. */
    @EntityGraph(attributePaths = {"vocabularySet", "vocabularySet.publishedVersion"})
    List<QuizWordGameConfig> findByGameVersionIdIn(Collection<Long> ids);
}
