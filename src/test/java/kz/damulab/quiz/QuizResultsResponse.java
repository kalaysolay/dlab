package kz.damulab.quiz;

// Контракт предыдущего бинарника нужен только для операторской проверки отката, не для приложения.

import java.util.List;

public record QuizResultsResponse(
        String code,
        String status,
        List<QuizParticipantResultResponse> results
) {
}
