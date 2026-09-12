package kz.damulab;

import static org.assertj.core.api.Assertions.assertThat;

import kz.damulab.ai.AiProviderCode;
import kz.damulab.ai.AiRuntimeSettingRepository;
import kz.damulab.ai.AiTranslationPromptCode;
import kz.damulab.ai.AiTranslationPromptRepository;
import kz.damulab.ai.AiUsageType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/** Проверяет рабочие значения, которые Flyway создаёт для нового переводчика. */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AiTranslationDefaultsIntegrationTest {

    @Autowired
    private AiRuntimeSettingRepository runtimeSettings;

    @Autowired
    private AiTranslationPromptRepository prompts;

    @Test
    void migrationSelectsDeepSeekAndCreatesBothPrompts() {
        var translations = runtimeSettings.findById(AiUsageType.TRANSLATIONS).orElseThrow();

        assertThat(translations.getProvider()).isEqualTo(AiProviderCode.DEEPSEEK);
        assertThat(translations.getModelName()).isEqualTo("deepseek-v4-pro");
        var translationPrompt = prompts.findById(AiTranslationPromptCode.TRANSLATE).orElseThrow();
        var explanationPrompt = prompts.findById(AiTranslationPromptCode.EXPLAIN).orElseThrow();
        assertThat(translationPrompt.getSystemPrompt())
                .contains("school learning application", "do not quote", "safe educational content only");
        assertThat(explanationPrompt.getSystemPrompt())
                .contains("school learning application", "do not quote", "safe educational content only");
    }
}
