package kz.damulab.quiz.catalog;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Доступ к VocabularySet; публикация и архивирование выполняются сервисами, не CRUD сам по себе. */
public interface VocabularySetRepository extends JpaRepository<VocabularySet, Long> {
    /** Загружает все источники с текущей публикацией одной выборкой для списка игр. */
    @EntityGraph(attributePaths = "publishedVersion")
    @Query("select s from VocabularySet s order by s.id")
    List<VocabularySet> findForGameDiagnostics();
    /** Возвращает наборы для административного списка в стабильном порядке. */
    // Шаблон читает названия и статусы версий после закрытия сессии (open-in-view=false).
    @EntityGraph(attributePaths = {"publishedVersion", "draftVersion"})
    List<VocabularySet> findAllByOrderByIdAsc();
    boolean existsByCodeIgnoreCase(String code);
}
