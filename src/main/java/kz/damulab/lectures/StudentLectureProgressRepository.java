package kz.damulab.lectures;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Репозиторий прогресса учеников по лекциям. */
public interface StudentLectureProgressRepository extends JpaRepository<StudentLectureProgress, Long> {

    /** Находит прогресс ученика по одной лекции. */
    Optional<StudentLectureProgress> findByStudentProfileIdAndLectureId(Long studentProfileId, Long lectureId);

    /** Загружает прогресс ученика одним запросом для построения списка лекций. */
    List<StudentLectureProgress> findByStudentProfileIdAndLectureIdIn(Long studentProfileId, List<Long> lectureIds);
}
