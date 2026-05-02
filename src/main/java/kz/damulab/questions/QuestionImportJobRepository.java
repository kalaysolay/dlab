package kz.damulab.questions;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionImportJobRepository extends JpaRepository<QuestionImportJob, Long> {
}
