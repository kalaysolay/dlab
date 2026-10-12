package kz.damulab.quiz.catalog;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Доступ к VocabularyItemVersion; публикация и архивирование выполняются сервисами, не CRUD сам по себе. */
public interface VocabularyItemVersionRepository extends JpaRepository<VocabularyItemVersion, Long> {
    @Query("select coalesce(max(v.versionNo), 0) from VocabularyItemVersion v where v.item.id = :itemId")
    int findMaxVersionNo(@Param("itemId") Long itemId);
    /** Отбирает только опубликованные текущие редакции, доступные всем требуемым классам. Пустая аудитория допускает лишь universal. */
    @Query("""
            select v.id from VocabularyItemVersion v
            join v.item o
            join o.set s
            where s.id = :setId
            and o.status <> kz.damulab.quiz.catalog.EditorialStatus.ARCHIVED
            and s.status <> kz.damulab.quiz.catalog.EditorialStatus.ARCHIVED
            and o.publishedVersion = v
            and v.status = kz.damulab.quiz.catalog.EditorialStatus.PUBLISHED
            and v.publishedAt is not null
            and s.publishedVersion.status = kz.damulab.quiz.catalog.EditorialStatus.PUBLISHED
            and s.publishedVersion.publishedAt is not null
            and (v.universalAudience = true or (:gradeCount > 0
            and (select count(g) from VocabularyItemVersion a
            join a.gradeIds g
            where a.id = v.id
            and g in :gradeIds) = :gradeCount))
            order by v.id
            """)
    List<Long> findEligibleIds(@Param("setId") Long setId, @Param("gradeIds") Set<Long> gradeIds, @Param("gradeCount") long gradeCount, Pageable page);
    /** Считает доступность тем же предикатом, не загружая весь банк в память. */
    @Query("""
            select count(v) from VocabularyItemVersion v
            join v.item o
            join o.set s
            where s.id = :setId
            and o.status <> kz.damulab.quiz.catalog.EditorialStatus.ARCHIVED
            and s.status <> kz.damulab.quiz.catalog.EditorialStatus.ARCHIVED
            and o.publishedVersion = v
            and v.status = kz.damulab.quiz.catalog.EditorialStatus.PUBLISHED
            and v.publishedAt is not null
            and s.publishedVersion.status = kz.damulab.quiz.catalog.EditorialStatus.PUBLISHED
            and s.publishedVersion.publishedAt is not null
            and (v.universalAudience = true or (:gradeCount > 0
            and (select count(g) from VocabularyItemVersion a
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
    /** Ищет возможные дубли/омонимы внутри набора; одинаковая строка не объединяет значения автоматически. */
    @Query("select v from VocabularyItemVersion v join fetch v.item i where i.set.id = :setId and v.sourceNormalized = :normalized")
    List<VocabularyItemVersion> findByNormalizedSource(@Param("setId") Long setId, @Param("normalized") String normalized);

    /** Ищет точные нормализованные совпадения исходного слова или любого перевода. */
    @Query("""
            select distinct v from VocabularyItemVersion v join fetch v.item i
            where (:setId is null or i.set.id = :setId)
            and (v.sourceNormalized = :normalized or exists (
                select a.id from VocabularyItemAnswer a where a.itemVersion = v and a.answerNormalized = :normalized))
            order by v.id
            """)
    List<VocabularyItemVersion> findPotentialDuplicates(@Param("setId") Long setId, @Param("normalized") String normalized);

    /** Загружает текущие опубликованные версии-кандидаты; аудитория и неоднозначность фильтруются общей policy. */
    @Query("""
            select distinct v from VocabularyItemVersion v join fetch v.item i join fetch i.set s
            join fetch s.publishedVersion left join fetch v.gradeIds
            where i.publishedVersion = v and v.status = kz.damulab.quiz.catalog.EditorialStatus.PUBLISHED
            and v.publishedAt is not null and i.status <> kz.damulab.quiz.catalog.EditorialStatus.ARCHIVED
            and s.status <> kz.damulab.quiz.catalog.EditorialStatus.ARCHIVED
            and s.publishedVersion.status = kz.damulab.quiz.catalog.EditorialStatus.PUBLISHED
            and s.publishedVersion.publishedAt is not null
            and (:sourceLanguage is null or s.sourceLanguage = :sourceLanguage)
            and (:targetLanguage is null or s.targetLanguage = :targetLanguage)
            and (:crossSet = true or s.id = :setId)
            order by v.id
            """)
    List<VocabularyItemVersion> findPublishedForGame(@Param("setId") Long setId, @Param("sourceLanguage") String sourceLanguage,
            @Param("targetLanguage") String targetLanguage, @Param("crossSet") boolean crossSet);
    /** Общая диагностика использует тот же предикат публикации без ограничения источника. */
    default List<VocabularyItemVersion> findPublishedCurrentVersions() { return findPublishedForGame(null, null, null, true); }
}
