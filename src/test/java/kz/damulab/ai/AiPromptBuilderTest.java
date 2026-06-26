package kz.damulab.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiPromptBuilderTest {

    private final AiPromptBuilder promptBuilder = new AiPromptBuilder();

    @Test
    void miniLecturePromptIncludesQuestionContextAndPedagogyRules() {
        MiniLectureGenerationRequest request = new MiniLectureGenerationRequest(
                "Математика",
                "Математика",
                "4 класс",
                "4 сынып",
                4,
                "Проценты",
                "Пайыздар",
                "Найди 15% от числа 80.",
                "80 санынан 15%-ын таб.",
                "-",
                "-",
                "12",
                "12"
        );

        String prompt = promptBuilder.miniLecturePrompt(request);

        assertThat(prompt)
                .contains("Найди 15% от числа 80.")
                .contains("correct_answer:")
                .contains("RU: 12")
                .contains("grade_no:")
                .contains("4 класс")
                .contains("Сначала объясняй смысл простыми словами")
                .contains("question_analysis — Разбор задачи")
                .contains("Если вопрос про проценты для младших классов")
                .contains("theory — не короче 80 символов")
                .doesNotContain("если не требуется подробное объяснение")
                .contains("\"ru\"")
                .contains("\"kz\"");
    }

    @Test
    void miniLectureSystemPromptRequiresMeaningBeforeFormulas() {
        assertThat(promptBuilder.miniLectureSystemPrompt())
                .contains("Explain the idea in simple words before showing formulas");
    }
}
