package kz.damulab.quiz;

public record QuizParticipantResponse(
        Long id,
        String displayName,
        boolean host,
        boolean ready,
        boolean currentStudent,
        int answeredRounds
) {
}
