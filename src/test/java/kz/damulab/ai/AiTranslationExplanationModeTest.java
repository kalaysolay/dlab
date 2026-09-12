package kz.damulab.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiTranslationExplanationModeTest {

    @Test
    void economyModeHasAConsiderablySmallerOutputBudget() {
        assertThat(AiTranslationExplanationMode.ECONOMY.maxOutputTokens()).isEqualTo(384);
        assertThat(AiTranslationExplanationMode.DETAILED.maxOutputTokens()).isEqualTo(1600);
        assertThat(AiTranslationExplanationMode.ECONOMY.maxOutputTokens())
                .isLessThan(AiTranslationExplanationMode.DETAILED.maxOutputTokens());
    }
}
