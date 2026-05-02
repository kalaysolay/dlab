package kz.damulab.lectures;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LectureCheckpointRepository extends JpaRepository<LectureCheckpoint, Long> {

    List<LectureCheckpoint> findByLectureVersionIdOrderBySortOrderAscIdAsc(Long lectureVersionId);

    long countByLectureVersionId(Long lectureVersionId);

    void deleteByLectureVersionId(Long lectureVersionId);
}
