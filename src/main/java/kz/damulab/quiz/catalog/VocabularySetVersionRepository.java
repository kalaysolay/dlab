package kz.damulab.quiz.catalog;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Доступ к VocabularySetVersion; публикация и архивирование выполняются сервисами, не CRUD сам по себе. */
public interface VocabularySetVersionRepository extends JpaRepository<VocabularySetVersion, Long> {
    @Query("select coalesce(max(v.versionNo), 0) from VocabularySetVersion v where v.set.id = :setId")
    int findMaxVersionNo(@Param("setId") Long setId);
}
