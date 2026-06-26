package kz.damulab.ai;

/**
 * Собирает HTML для сохранения в mini_lecture_ru / mini_lecture_kk из структурированных полей.
 */
public final class MiniLectureHtmlComposer {

    private static final String[] SECTION_LABELS_RU = {
            "Объяснение",
            "Разбор задачи",
            "Типичная ошибка",
            "Похожий пример",
            "Что запомнить"
    };

    private static final String[] SECTION_LABELS_KK = {
            "Түсіндіру",
            "Есепті талдау",
            "Жиі кездесетін қате",
            "Ұқсас мысал",
            "Есте сақтау"
    };

    private MiniLectureHtmlComposer() {
    }

    public static AiMiniLectureResult toResult(MiniLectureStructuredPayload payload) {
        return new AiMiniLectureResult(
                toHtml(payload.ru(), SECTION_LABELS_RU),
                toHtml(payload.kz(), SECTION_LABELS_KK)
        );
    }

    public static String toHtml(MiniLectureLangBlock block, String[] sectionLabels) {
        StringBuilder b = new StringBuilder(512);
        b.append("<article class=\"mini-lecture\">");
        b.append("<h3>").append(escapeHtml(block.title())).append("</h3>");
        appendSection(b, sectionLabels[0], block.theory());
        appendSection(b, sectionLabels[1], block.questionAnalysis());
        appendSection(b, sectionLabels[2], block.commonMistake());
        appendSection(b, sectionLabels[3], block.exampleAnalysis());
        appendSection(b, sectionLabels[4], block.summary());
        b.append("</article>");
        return b.toString();
    }

    private static void appendSection(StringBuilder b, String heading, String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        b.append("<h4>").append(escapeHtml(heading)).append("</h4>");
        b.append("<div class=\"mini-lecture-section\">");
        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            b.append("<p>").append(escapeHtml(trimmed)).append("</p>");
        }
        b.append("</div>");
    }

    static String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
