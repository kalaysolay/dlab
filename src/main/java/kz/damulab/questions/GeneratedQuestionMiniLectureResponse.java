package kz.damulab.questions;

/** Результат генерации и сохранения мини-лекции прямо из карточки предпросмотра. */
public record GeneratedQuestionMiniLectureResponse(
        QuestionPreviewResponse preview,
        boolean stubMode
) {
}
