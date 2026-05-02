package kz.damulab.ai;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiGeneratedQuestionBatchRepository extends JpaRepository<AiGeneratedQuestionBatch, Long> {

    Optional<AiGeneratedQuestionBatch> findByJobId(Long jobId);
}
