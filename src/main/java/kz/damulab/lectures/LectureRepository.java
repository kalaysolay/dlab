package kz.damulab.lectures;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LectureRepository extends JpaRepository<Lecture, Long>, JpaSpecificationExecutor<Lecture> {

    List<Lecture> findByStatusOrderByUpdatedAtDesc(LectureStatus status);
}
