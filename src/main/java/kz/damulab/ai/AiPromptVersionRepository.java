package kz.damulab.ai;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiPromptVersionRepository
        extends JpaRepository<AiPromptVersion, AiPromptVersionId> {

    Optional<AiPromptVersion> findByPromptCodeAndVersionNo(AiPromptCode promptCode, int versionNo);

    Optional<AiPromptVersion> findFirstByPromptCodeOrderByVersionNoDesc(AiPromptCode promptCode);
}
