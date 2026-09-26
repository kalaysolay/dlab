package kz.damulab.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class AiLectureQualityValidatorTest {

    private static final AiLectureGenerationRequest REQUEST = new AiLectureGenerationRequest(
            "Математика", "Математика", 5, "5 класс", "5 сынып",
            "Дроби", "Бөлшектер", "Без дополнительных требований"
    );

    @Test
    void rejectsShortLectureBelowRequiredScore() {
        AiLectureStructuredPayload payload = new AiLectureStructuredPayload(
                language("Дроби", List.of(new AiLectureSectionPayload("Кратко", List.of("Мало текста"), List.of()))),
                language("Бөлшектер", List.of(new AiLectureSectionPayload("Қысқаша", List.of("Мәтін аз"), List.of())))
        );

        AiLectureQualityReport report = AiLectureQualityValidator.evaluate(payload, REQUEST);

        assertThat(report.score()).isLessThan(90);
        assertThat(report.minimumScore()).isEqualTo(90);
        assertThatThrownBy(() -> AiLectureQualityValidator.requireAccepted(payload, REQUEST))
                .isInstanceOf(AiLectureQualityException.class);
    }

    @Test
    void configuredThresholdChangesAcceptanceWithoutChangingScore() {
        AiLectureStructuredPayload payload = new AiLectureStructuredPayload(
                completeLanguage("Дроби"),
                completeLanguage("Бөлшектер")
        );

        AiLectureQualityReport report = AiLectureQualityValidator.requireAccepted(payload, REQUEST, 90);

        assertThat(report.score()).isEqualTo(90);
        assertThat(report.minimumScore()).isEqualTo(90);
        assertThatThrownBy(() -> AiLectureQualityValidator.requireAccepted(payload, REQUEST, 95))
                .isInstanceOf(AiLectureQualityException.class);
    }

    @Test
    void composerEscapesTextAndCreatesCanonicalFormulaEmbed() {
        AiLectureLanguagePayload payload = language(
                "Дроби",
                List.of(new AiLectureSectionPayload(
                        "Вычисление <script>",
                        List.of("Безопасный текст & пояснение"),
                        List.of("\\frac{a}{b}")
                ))
        );

        String html = AiLectureHtmlComposer.toHtml(payload);

        assertThat(html).contains("Вычисление &lt;script&gt;")
                .contains("Безопасный текст &amp; пояснение")
                .contains("class=\"ql-formula\"")
                .contains("data-value=\"\\frac{a}{b}\"")
                .doesNotContain("<script>");
    }

    private static AiLectureLanguagePayload language(String title, List<AiLectureSectionPayload> sections) {
        return new AiLectureLanguagePayload(title, "Введение в тему " + title, sections, "Итог по теме " + title);
    }

    private static AiLectureLanguagePayload completeLanguage(String title) {
        String paragraph = ("Подробное объяснение темы " + title
                + " с правилом, обоснованием и понятным учебным примером для ученика. ").repeat(3);
        return language(title, List.of(
                new AiLectureSectionPayload("Основы", List.of(paragraph, paragraph), List.of("")),
                new AiLectureSectionPayload("Применение", List.of(paragraph, paragraph), List.of()),
                new AiLectureSectionPayload("Проверка", List.of(paragraph, paragraph), List.of())
        ));
    }
}
