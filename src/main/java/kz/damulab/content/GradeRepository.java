package kz.damulab.content;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeRepository extends JpaRepository<Grade, Long> {

    List<Grade> findAllByOrderByGradeNoAsc();

    Optional<Grade> findByGradeNo(Integer gradeNo);
}
