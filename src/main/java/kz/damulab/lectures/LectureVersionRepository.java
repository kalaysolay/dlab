package kz.damulab.lectures;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LectureVersionRepository extends JpaRepository<LectureVersion, Long> {

    boolean existsByTopicId(Long topicId);

    @Query("""
            select coalesce(max(v.versionNo), 0)
            from LectureVersion v
            where v.lecture.id = :lectureId
            """)
    int findMaxVersionNoByLectureId(@Param("lectureId") Long lectureId);
}
