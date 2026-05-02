package kz.damulab.content;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findBySubjectIdAndGradeIdOrderByTitleRuAsc(Long subjectId, Long gradeId);

    long countBySubjectIdAndGradeId(Long subjectId, Long gradeId);

    long countByParentTopicId(Long parentTopicId);

    boolean existsByParentTopicId(Long parentTopicId);

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
