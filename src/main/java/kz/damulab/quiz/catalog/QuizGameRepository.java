package kz.damulab.quiz.catalog;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Доступ к QuizGame; публикация и архивирование выполняются сервисами, не CRUD сам по себе. */
public interface QuizGameRepository extends JpaRepository<QuizGame, Long> {
    /** Загружает список с обеими редакциями без отдельных запросов на каждую строку. */
    @EntityGraph(attributePaths = {"publishedVersion", "draftVersion"})
    @Query("select g from QuizGame g order by g.updatedAt desc, g.id")
    List<QuizGame> findAdminGames();

    /** Загружает обе редакции для SSR вне сервисной транзакции: open-in-view в приложении выключен. */
    @EntityGraph(attributePaths = {"publishedVersion", "draftVersion"})
    @Query("select g from QuizGame g where g.id = :id")
    Optional<QuizGame> findAdminGame(@Param("id") Long id);

    /** Сериализует редактирование, публикацию, архив и скрытие одной стабильной игры. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from QuizGame g where g.id = :id")
    Optional<QuizGame> lockById(@Param("id") Long id);
    /** Возвращает карточки с публикацией одним запросом, без N+1 по версии; архив/скрытие исключены. */
    @EntityGraph(attributePaths = {"publishedVersion", "publishedVersion.gradeIds"})
    @Query("""
            select g from QuizGame g
            join g.publishedVersion v
            where g.catalogVisible = true
            and g.status <> kz.damulab.quiz.catalog.EditorialStatus.ARCHIVED
            and v.status = kz.damulab.quiz.catalog.EditorialStatus.PUBLISHED
            and v.publishedAt is not null
            order by v.catalogOrder, g.code
            """)
    List<QuizGame> findPublishedCatalog(Pageable page);
    /** Находит стабильную игру по серверному коду. */
    Optional<QuizGame> findByCode(String code);
}
