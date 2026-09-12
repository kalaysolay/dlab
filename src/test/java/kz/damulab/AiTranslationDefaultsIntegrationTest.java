package kz.damulab;

import static org.assertj.core.api.Assertions.assertThat;

import kz.damulab.ai.AiProviderCode;
import kz.damulab.ai.AiPromptCode;
import kz.damulab.ai.AiPromptOutputFormat;
import kz.damulab.ai.AiPromptRepository;
import kz.damulab.ai.AiPromptService;
import kz.damulab.ai.AiRuntimeSettingRepository;
import kz.damulab.ai.AiTranslationExplanationMode;
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
    private AiPromptService prompts;

    @Autowired
    private AiPromptRepository promptDefinitions;

    @Test
    void migrationSelectsDeepSeekAndCreatesBothPrompts() {
        var translations = runtimeSettings.findById(AiUsageType.TRANSLATIONS).orElseThrow();

        assertThat(translations.getProvider()).isEqualTo(AiProviderCode.DEEPSEEK);
        assertThat(translations.getModelName()).isEqualTo("deepseek-v4-pro");
        var translationPrompt = prompts.current(AiPromptCode.TRANSLATION_TRANSLATE);
        var explanationPrompt = prompts.current(AiPromptCode.TRANSLATION_EXPLAIN);
        assertThat(translationPrompt.version()).isEqualTo(1);
        assertThat(explanationPrompt.version()).isEqualTo(2);
        assertThat(translationPrompt.systemTemplate())
                .contains("school learning application", "do not quote", "safe educational content only");
        assertThat(explanationPrompt.systemTemplate())
                .contains("school learning application", "do not quote", "safe educational content only", "120 words");
        assertThat(explanationPrompt.userTemplate()).contains("{explanationMode}");
        assertThat(promptDefinitions.findById(AiPromptCode.TRANSLATION_EXPLAIN).orElseThrow().getOutputFormat())
                .isEqualTo(AiPromptOutputFormat.MARKDOWN);
    }

    @Test
    void economyModeIsRenderedAsTrustedApplicationData() {
        var rendered = prompts.render(AiPromptCode.TRANSLATION_EXPLAIN, java.util.Map.of(
                "sourceLanguage", "Kazakh",
                "targetLanguage", "Russian",
                "explanationLanguage", "Russian",
                "sourceTextJson", "\"Сәлем\"",
                "translatedTextJson", "\"Привет\"",
                "explanationMode", AiTranslationExplanationMode.ECONOMY.name()
        ));

        assertThat(rendered.userPrompt()).contains("Explanation mode: ECONOMY");
    }
}
