package kz.damulab.quiz.catalog;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Доступ к FactStatementVersion; публикация и архивирование выполняются сервисами, не CRUD сам по себе. */
public interface FactStatementVersionRepository extends JpaRepository<FactStatementVersion, Long> {
    /** Загружает текущие публикации с аудиторией одним запросом для диагностики игр. */
    @Query("""
            select distinct v from FactStatementVersion v join fetch v.statement o join fetch o.set s
            left join fetch v.gradeIds
            where o.publishedVersion = v and v.status = kz.damulab.quiz.catalog.EditorialStatus.PUBLISHED
            and v.publishedAt is not null and o.status <> kz.damulab.quiz.catalog.EditorialStatus.ARCHIVED
            and (:setId is null or s.id = :setId)
            order by v.id
            """)
    List<FactStatementVersion> findPublishedCurrentVersionsInSet(@Param("setId") Long setId);
    /** Диагностика получает все наборы тем же запросом, движок ограничивает его выбранным набором. */
    default List<FactStatementVersion> findPublishedCurrentVersions() { return findPublishedCurrentVersionsInSet(null); }
    @Query("select coalesce(max(v.versionNo), 0) from FactStatementVersion v where v.statement.id = :statementId")
    int findMaxVersionNo(@Param("statementId") Long statementId);
    /** Отбирает только опубликованные текущие редакции, доступные всем требуемым классам. Пустая аудитория допускает лишь universal. */
    @Query("""
            select v.id from FactStatementVersion v
            join v.statement o
            join o.set s
            where s.id = :setId
            and o.status <> kz.damulab.quiz.catalog.EditorialStatus.ARCHIVED
            and s.status <> kz.damulab.quiz.catalog.EditorialStatus.ARCHIVED
            and o.publishedVersion = v
            and v.status = kz.damulab.quiz.catalog.EditorialStatus.PUBLISHED
            and v.publishedAt is not null
            and (v.universalAudience = true or (:gradeCount > 0
            and (select count(g) from FactStatementVersion a
            join a.gradeIds g
            where a.id = v.id
            and g in :gradeIds) = :gradeCount))
            order by v.id
            """)
    List<Long> findEligibleIds(@Param("setId") Long setId, @Param("gradeIds") Set<Long> gradeIds, @Param("gradeCount") long gradeCount, Pageable page);
    /** Считает доступность тем же предикатом, не загружая весь банк в память. */
    @Query("""
            select count(v) from FactStatementVersion v
            join v.statement o
            join o.set s
            where s.id = :setId
            and o.status <> kz.damulab.quiz.catalog.EditorialStatus.ARCHIVED
            and s.status <> kz.damulab.quiz.catalog.EditorialStatus.ARCHIVED
            and o.publishedVersion = v
            and v.status = kz.damulab.quiz.catalog.EditorialStatus.PUBLISHED
            and v.publishedAt is not null
            and (v.universalAudience = true or (:gradeCount > 0
            and (select count(g) from FactStatementVersion a
            join a.gradeIds g
            where a.id = v.id
            and g in :gradeIds) = :gradeCount))
            """)
    long countEligible(@Param("setId") Long setId, @Param("gradeIds") Set<Long> gradeIds, @Param("gradeCount") long gradeCount);
    /** Безопасный вход для расчёта количества: количество классов всегда согласовано с их множеством. */
    default long countEligible(Long setId, Set<Long> gradeIds) {
        return countEligible(setId, gradeIds.isEmpty() ? Set.of(-1L) : gradeIds, gradeIds.size());
    }
    /** Безопасный вход для выборки: пустой IN заменяется невозможным ID, но не универсальностью банка. */
    default List<Long> findEligibleIds(Long setId, Set<Long> gradeIds, Pageable page) {
        return findEligibleIds(setId, gradeIds.isEmpty() ? Set.of(-1L) : gradeIds, gradeIds.size(), page);
    }
}
