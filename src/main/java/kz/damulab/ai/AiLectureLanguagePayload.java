package kz.damulab.ai;

import java.util.List;

/** Структурированная языковая версия лекции до преобразования в безопасный HTML. */
public record AiLectureLanguagePayload(
        String title,
        String introduction,
        List<AiLectureSectionPayload> sections,
        String conclusion
) {
}
