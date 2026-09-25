package kz.damulab.ai;

import java.util.List;

/**
 * Собирает HTML только из фиксированных тегов. Текст модели всегда экранируется,
 * а LaTeX хранится в формате Quill formula embed для существующего KaTeX-renderer.
 */
public final class AiLectureHtmlComposer {

    private AiLectureHtmlComposer() {
    }

    public static String toHtml(AiLectureLanguagePayload block) {
        StringBuilder html = new StringBuilder(2048);
        appendParagraph(html, block.introduction(), "lecture-introduction");
        for (AiLectureSectionPayload section : safeList(block.sections())) {
            html.append("<h2>").append(escape(section.heading())).append("</h2>");
            for (String paragraph : safeList(section.paragraphs())) {
                appendParagraph(html, paragraph, null);
            }
            for (String formula : safeList(section.formulas())) {
                html.append("<p class=\"lecture-formula\"><span class=\"ql-formula\" data-value=\"")
                        .append(escape(formula.trim()))
                        .append("\" contenteditable=\"false\"></span></p>");
            }
        }
        appendParagraph(html, block.conclusion(), "lecture-conclusion");
        return html.toString();
    }

    private static void appendParagraph(StringBuilder html, String value, String cssClass) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String paragraph : value.split("\\R{2,}")) {
            if (paragraph.isBlank()) {
                continue;
            }
            html.append("<p");
            if (cssClass != null) {
                html.append(" class=\"").append(cssClass).append("\"");
            }
            html.append(">").append(escape(paragraph.trim()).replace("\n", "<br>")).append("</p>");
        }
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
