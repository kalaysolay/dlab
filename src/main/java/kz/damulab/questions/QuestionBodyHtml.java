package kz.damulab.questions;

import java.util.Set;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Единая точка преобразования текста вопроса в безопасный HTML.
 *
 * <p>Старые версии вопросов содержат обычный текст, а новые — HTML из Quill. Метод
 * {@link #render(String)} поддерживает оба формата: plain text экранируется и оборачивается
 * в абзацы, HTML очищается по узкому белому списку. Благодаря этому шаблоны могут использовать
 * {@code th:utext}, не открывая XSS и не требуя миграции старых записей.</p>
 */
@Component("questionBodyHtml")
public class QuestionBodyHtml {

    private static final Pattern HTML_MARKUP = Pattern.compile(
            "(?is)<\\s*/?\\s*(p|div|br|strong|b|em|i|u|s|ol|ul|li|a|img|blockquote|pre|code|sub|sup|h2|h3|h4)(?:\\s|/?>)"
    );
    private static final Pattern QUESTION_IMAGE_URL = Pattern.compile(
            "^/files/question-images/[a-zA-Z0-9._-]+$"
    );
    private static final Set<String> EMPTY_PARAGRAPH_HTML = Set.of("", "<p></p>", "<p><br></p>");

    private final Safelist safelist = new Safelist()
            .addTags("p", "div", "br", "strong", "b", "em", "i", "u", "s", "ol", "ul", "li",
                    "a", "img", "blockquote", "pre", "code", "sub", "sup", "h2", "h3", "h4")
            .addAttributes("a", "href", "title", "target", "rel")
            .addAttributes("img", "src", "alt", "title", "width", "height")
            .addAttributes("li", "data-list")
            .addProtocols("a", "href", "http", "https", "mailto")
            .preserveRelativeLinks(true);

    /**
     * Возвращает безопасный HTML для показа. Обычный текст старых вопросов становится
     * последовательностью {@code <p>...</p>}; одиночные переводы строк становятся {@code <br>}.
     */
    public String render(String storedBody) {
        if (storedBody == null || storedBody.isBlank()) {
            return "";
        }
        if (!HTML_MARKUP.matcher(storedBody).find()) {
            return plainTextToHtml(storedBody);
        }

        Document dirty = Jsoup.parseBodyFragment(storedBody);
        Document clean = new Cleaner(safelist).clean(dirty);
        clean.outputSettings().prettyPrint(false);
        clean.select("img").forEach(this::keepOnlyStoredQuestionImage);
        clean.select("a[target=_blank]").attr("rel", "noopener noreferrer");
        return clean.body().html();
    }

    /**
     * Очищает HTML из редактора. Plain text из API/AI/Excel оставляет plain text — это сохраняет
     * обратную совместимость генерации и существующего API; при показе {@link #render(String)}
     * всё равно безопасно оборачивает его в абзацы.
     */
    public String normalizeForStorage(String submittedBody) {
        if (submittedBody == null || submittedBody.isBlank()) {
            return "";
        }
        return HTML_MARKUP.matcher(submittedBody).find()
                ? render(submittedBody).trim()
                : submittedBody.trim();
    }

    /** Текстовое представление для AI-промптов, поиска и мест, где разметка не нужна. */
    public String toPlainText(String body) {
        String html = render(body);
        return html.isBlank() ? "" : Jsoup.parseBodyFragment(html).body().text();
    }

    /** Пустой абзац Quill не считается заполненным вопросом; отдельная картинка считается контентом. */
    public boolean hasMeaningfulContent(String body) {
        String html = render(body).trim();
        if (EMPTY_PARAGRAPH_HTML.contains(html)) {
            return false;
        }
        Element parsed = Jsoup.parseBodyFragment(html).body();
        return !parsed.text().isBlank() || parsed.selectFirst("img") != null;
    }

    private String plainTextToHtml(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        for (String paragraph : normalized.split("\\n\\s*\\n")) {
            html.append("<p>")
                    .append(Entities.escape(paragraph).replace("\n", "<br>"))
                    .append("</p>");
        }
        return html.toString();
    }

    private void keepOnlyStoredQuestionImage(Element image) {
        String src = image.attr("src");
        if (!QUESTION_IMAGE_URL.matcher(src).matches()) {
            image.remove();
            return;
        }
        image.attr("loading", "lazy");
    }
}
