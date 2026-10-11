package kz.damulab.quiz;

// Контракт предыдущего бинарника нужен только для операторской проверки отката, не для приложения.

public record QuizParticipantResponse(
        Long id,
        String displayName,
        boolean host,
        boolean ready,
        boolean currentStudent,
        int answeredRounds
) {
}
