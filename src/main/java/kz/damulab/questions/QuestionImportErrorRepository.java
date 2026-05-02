package kz.damulab.questions;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionImportErrorRepository extends JpaRepository<QuestionImportError, Long> {

    List<QuestionImportError> findByJobIdOrderByRowNoAsc(Long jobId);
}
