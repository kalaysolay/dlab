package kz.damulab.questions;

public record QuestionEditView(
        Long questionId,
        int versionNo,
        String status,
        Long subjectId,
        Long gradeId,
        QuestionForm form
) {
}
