package kz.damulab.content;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Выборка тем. Списковые методы — только активные ({@code deleted_at is null}).
 * {@link #findById} по-прежнему возвращает и soft-deleted (история / restore).
 */
public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findBySubject_IdAndDeletedAtIsNullOrderByTitleRuAsc(Long subjectId);

    List<Topic> findBySubjectIdAndGradeIdAndDeletedAtIsNullOrderByTitleRuAsc(Long subjectId, Long gradeId);

    long countBySubjectIdAndGradeIdAndDeletedAtIsNull(Long subjectId, Long gradeId);

    long countByParentTopicIdAndDeletedAtIsNull(Long parentTopicId);

    boolean existsByParentTopicIdAndDeletedAtIsNull(Long parentTopicId);

    List<Topic> findByParentTopicIdAndDeletedAtIsNullOrderByTitleRuAsc(Long parentTopicId);

    @Query("""
            select t
            from Topic t
            where t.subject.id = :subjectId
              and t.grade.id = :gradeId
              and (
                    (:parentId is null and t.parentTopic is null)
                    or (:parentId is not null and t.parentTopic.id = :parentId)
              )
              and lower(t.code) = lower(:code)
              and t.deletedAt is null
            """)
    Optional<Topic> findByScopeAndCode(
            @Param("subjectId") Long subjectId,
            @Param("gradeId") Long gradeId,
            @Param("parentId") Long parentId,
            @Param("code") String code
    );

    /** Soft-deleted тема с тем же scope+code — для revive при импорте. */
    @Query("""
            select t
            from Topic t
            where t.subject.id = :subjectId
              and t.grade.id = :gradeId
              and (
                    (:parentId is null and t.parentTopic is null)
                    or (:parentId is not null and t.parentTopic.id = :parentId)
              )
              and lower(t.code) = lower(:code)
              and t.deletedAt is not null
            """)
    Optional<Topic> findDeletedByScopeAndCode(
            @Param("subjectId") Long subjectId,
            @Param("gradeId") Long gradeId,
            @Param("parentId") Long parentId,
            @Param("code") String code
    );

    @Query("""
            select count(t) > 0
            from Topic t
            where t.subject.id = :subjectId
              and t.grade.id = :gradeId
              and (
                    (:parentId is null and t.parentTopic is null)
                    or (:parentId is not null and t.parentTopic.id = :parentId)
              )
              and (
                    lower(t.code) = lower(:code)
                    or lower(t.titleRu) = lower(:titleRu)
                    or lower(t.titleKk) = lower(:titleKk)
              )
              and t.deletedAt is null
              and (:excludeId is null or t.id <> :excludeId)
            """)
    boolean existsDuplicate(
            @Param("subjectId") Long subjectId,
            @Param("gradeId") Long gradeId,
            @Param("parentId") Long parentId,
            @Param("code") String code,
            @Param("titleRu") String titleRu,
            @Param("titleKk") String titleKk,
            @Param("excludeId") Long excludeId
    );
}
