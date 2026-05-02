package kz.damulab.lectures;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LectureAttachmentRepository extends JpaRepository<LectureAttachment, Long> {

    List<LectureAttachment> findByLectureVersionIdOrderBySortOrderAscIdAsc(Long lectureVersionId);

    void deleteByLectureVersionId(Long lectureVersionId);
}
