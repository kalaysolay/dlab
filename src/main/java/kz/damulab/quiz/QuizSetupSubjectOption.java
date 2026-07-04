package kz.damulab.quiz;

import java.util.List;

public record QuizSetupSubjectOption(
        Long id,
        String code,
        String titleRu,
        String titleKk,
        long questionCount,
        List<QuizSetupGradeOption> grades
) {
}
