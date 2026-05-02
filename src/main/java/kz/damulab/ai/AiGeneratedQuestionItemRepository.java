package kz.damulab.ai;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiGeneratedQuestionItemRepository extends JpaRepository<AiGeneratedQuestionItem, Long> {

    List<AiGeneratedQuestionItem> findByBatchIdOrderByIdAsc(Long batchId);
}
