package kz.damulab.questions;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionBodyHtmlTest {

    private final QuestionBodyHtml html = new QuestionBodyHtml();

    @Test
    void rendersLegacyPlainTextAsEscapedParagraphs() {
        assertThat(html.render("Сравните 2 < 3\nи выберите ответ.\n\nВторая часть"))
                .isEqualTo("<p>Сравните 2 &lt; 3<br>и выберите ответ.</p><p>Вторая часть</p>");
    }

    @Test
    void sanitizesEditorHtmlAndKeepsOnlyStoredQuestionImages() {
        String dirty = "<p onclick=\"alert(1)\">Текст<script>alert(2)</script></p>"
                + "<img src=\"/files/question-images/123e4567-e89b-12d3-a456-426614174000.png\" onerror=\"alert(3)\">"
                + "<img src=\"https://evil.example/image.png\">";

        String rendered = html.render(dirty);

        assertThat(rendered)
                .contains("<p>Текст</p>")
                .contains("src=\"/files/question-images/123e4567-e89b-12d3-a456-426614174000.png\"")
                .contains("loading=\"lazy\"")
                .doesNotContain("script", "onclick", "onerror", "evil.example");
    }

    @Test
    void keepsPlainAiPayloadPlainButNormalizesRichEditorHtml() {
        assertThat(html.normalizeForStorage("Сгенерированный вопрос"))
                .isEqualTo("Сгенерированный вопрос");
        assertThat(html.normalizeForStorage("<p><strong>Редактор</strong></p>"))
                .isEqualTo("<p><strong>Редактор</strong></p>");
    }

    @Test
    void quillEmptyParagraphIsNotMeaningfulButImageIs() {
        assertThat(html.hasMeaningfulContent("<p><br></p>")).isFalse();
        assertThat(html.hasMeaningfulContent(
                "<p><img src=\"/files/question-images/123e4567-e89b-12d3-a456-426614174000.png\"></p>"
        )).isTrue();
    }

    @Test
    void convertsRichBodyToPlainTextForAiPrompt() {
        assertThat(html.toPlainText("<p>Найдите <strong>20%</strong>.</p><p>[[1]]</p>"))
                .isEqualTo("Найдите 20%. [[1]]");
    }
}
