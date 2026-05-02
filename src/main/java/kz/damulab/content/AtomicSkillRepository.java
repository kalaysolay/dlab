package kz.damulab.content;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AtomicSkillRepository extends JpaRepository<AtomicSkill, Long> {

    List<AtomicSkill> findByTopicIdOrderByTitleRuAsc(Long topicId);

    long countByTopicId(Long topicId);

    boolean existsByTopicId(Long topicId);

    @Query("""
            select count(s) > 0
            from AtomicSkill s
            where s.topic.id = :topicId
              and (
                    lower(s.code) = lower(:code)
                    or lower(s.titleRu) = lower(:titleRu)
                    or lower(s.titleKk) = lower(:titleKk)
              )
              and (:excludeId is null or s.id <> :excludeId)
            """)
    boolean existsDuplicate(
            @Param("topicId") Long topicId,
            @Param("code") String code,
            @Param("titleRu") String titleRu,
            @Param("titleKk") String titleKk,
            @Param("excludeId") Long excludeId
    );
}
