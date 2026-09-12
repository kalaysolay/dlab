package kz.damulab.ai;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiPromptRepository extends JpaRepository<AiPrompt, AiPromptCode> {

    /** Блокирует метаданные на время создания следующей версии. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select prompt from AiPrompt prompt where prompt.code = :code")
    Optional<AiPrompt> findByCodeForUpdate(@Param("code") AiPromptCode code);
}
