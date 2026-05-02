package kz.damulab.questions;

public record MiniLectureDraftResponse(
        String explanationRu,
        String explanationKk,
        String miniLectureRu,
        String miniLectureKk,
        String correctAnswerRu,
        String correctAnswerKk
) {
}
