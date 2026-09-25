package kz.damulab.ai;

import java.util.List;

/** Один смысловой раздел лекции; формулы отделены от прозы для безопасного KaTeX-рендеринга. */
public record AiLectureSectionPayload(String heading, List<String> paragraphs, List<String> formulas) {
}
