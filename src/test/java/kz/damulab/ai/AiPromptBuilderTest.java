package kz.damulab.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import kz.damulab.questions.QuestionType;

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

    @Test
    void questionPromptIncludesFewShotExamplesWithAntiCopyInstruction() {
        // Запрос с одним эталоном темы: в промпте должен появиться few-shot блок,
        // тело эталона, его ключ ответа и строгий запрет копировать пример дословно.
        AiExamplePayload example = new AiExamplePayload(
                QuestionType.SCQ,
                2,
                "Сколько будет 2+2?",
                "2+2 неше?",
                "[{\"label\":\"A\",\"textRu\":\"4\",\"correct\":true}]"
        );
        String prompt = promptBuilder.questionGenerationPrompt(requestWithExamples(List.of(example)));

        assertThat(prompt)
                .contains("Reference examples from this topic")
                .contains("do not copy these examples verbatim")
                .contains("Сколько будет 2+2?")
                .contains("Example 1 (SCQ, difficulty 2)")
                .contains("[{\"label\":\"A\",\"textRu\":\"4\",\"correct\":true}]");
    }

    @Test
    void questionPromptOmitsExamplesBlockWhenNoExamples() {
        // Пустой/отсутствующий список эталонов => промпт не содержит few-shot блока
        // (поведение как до фичи), чтобы не тратить токены и не путать модель.
        assertThat(promptBuilder.questionGenerationPrompt(requestWithExamples(null)))
                .doesNotContain("Reference examples from this topic");
        assertThat(promptBuilder.questionGenerationPrompt(requestWithExamples(List.of())))
                .doesNotContain("Reference examples from this topic");
    }

    private AiQuestionGenerationRequest requestWithExamples(List<AiExamplePayload> examples) {
        return new AiQuestionGenerationRequest(
                "Математика",
                "Математика",
                4,
                "Сложение",
                "Қосу",
                null,
                null,
                QuestionType.SCQ,
                2,
                5,
                AiLanguageMode.RU_KK,
                "Один вопрос на жизненный кейс",
                examples
        );
    }
}
