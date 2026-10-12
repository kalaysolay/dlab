package kz.damulab.quiz.catalog;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Доступ к VocabularyDistractorExclusion; публикация и архивирование выполняются сервисами, не CRUD сам по себе. */
public interface VocabularyDistractorExclusionRepository extends JpaRepository<VocabularyDistractorExclusion, Long> {
    /** Учитывает направленные записи в обе стороны: запрещённую пару нельзя обойти перестановкой. */
    @Query("""
            select e from VocabularyDistractorExclusion e
            join fetch e.itemVersion
            join fetch e.excludedItemVersion
            where e.itemVersion.id in :ids or e.excludedItemVersion.id in :ids
            """)
    List<VocabularyDistractorExclusion> findForVersions(@Param("ids") Collection<Long> ids);
}
