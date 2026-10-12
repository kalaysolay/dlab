package kz.damulab.quiz.catalog;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Доступ к VocabularyItem; публикация и архивирование выполняются сервисами, не CRUD сам по себе. */
public interface VocabularyItemRepository extends JpaRepository<VocabularyItem, Long> {
    /** Загружает слово с версиями и аудиторией для редактора при open-in-view=false. */
    @Override
    @EntityGraph(attributePaths = {"set", "publishedVersion", "draftVersion",
            "publishedVersion.gradeIds", "draftVersion.gradeIds"})
    Optional<VocabularyItem> findById(Long id);

    // Фильтр по классу читает аудиторию текущей версии вне сессии БД.
    @EntityGraph(attributePaths = {"set", "publishedVersion", "draftVersion",
            "publishedVersion.gradeIds", "draftVersion.gradeIds"})
    List<VocabularyItem> findAllByOrderByIdAsc();
}
