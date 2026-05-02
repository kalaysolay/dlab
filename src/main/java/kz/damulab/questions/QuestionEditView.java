package kz.damulab.questions;

public record QuestionEditView(
        Long questionId,
        int versionNo,
        String status,
        Long subjectId,
        Long gradeId,
        QuestionForm form,
        boolean hasPendingDraft,
        Integer draftVersionNo,
        Integer liveVersionNo
) {
}
