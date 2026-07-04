package kz.damulab.quiz;

import java.util.List;

public record QuizSetupCatalog(
        String sourceType,
        List<QuizSetupSubjectOption> subjects
) {
}
