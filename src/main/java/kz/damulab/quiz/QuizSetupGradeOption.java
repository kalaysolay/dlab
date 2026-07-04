package kz.damulab.quiz;

public record QuizSetupGradeOption(
        Long id,
        Integer gradeNo,
        String titleRu,
        String titleKk,
        long questionCount
) {
}
