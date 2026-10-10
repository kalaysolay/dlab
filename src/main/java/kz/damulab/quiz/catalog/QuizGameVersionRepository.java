package kz.damulab.quiz.catalog;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Доступ к QuizGameVersion; публикация и архивирование выполняются сервисами, не CRUD сам по себе. */
public interface QuizGameVersionRepository extends JpaRepository<QuizGameVersion, Long> {
    /** Выдаёт следующий номер под блокировкой владельца, включая прежние черновики. */
    @Query("select coalesce(max(v.versionNo), 0) from QuizGameVersion v where v.game.id = :id")
    int findMaxVersionNo(@Param("id") Long id);

    /** Загружает классы всех отображаемых редакций одной выборкой. */
    @EntityGraph(attributePaths = "gradeIds")
    List<QuizGameVersion> findByIdIn(Collection<Long> ids);
}
