package kz.damulab.quiz.catalog;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Доступ к VocabularyItem; публикация и архивирование выполняются сервисами, не CRUD сам по себе. */
public interface VocabularyItemRepository extends JpaRepository<VocabularyItem, Long> {
    @EntityGraph(attributePaths = {"set", "publishedVersion", "draftVersion"})
    List<VocabularyItem> findAllByOrderByIdAsc();
}
