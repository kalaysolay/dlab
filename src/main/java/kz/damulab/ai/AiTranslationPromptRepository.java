package kz.damulab.ai;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiTranslationPromptRepository
        extends JpaRepository<AiTranslationPrompt, AiTranslationPromptCode> {
}
