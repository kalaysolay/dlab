package kz.damulab.testing;

import java.time.OffsetDateTime;
import java.util.List;

public record TestSessionResponse(
        Long id,
        String status,
        String testType,
        Long subjectId,
        String subjectTitle,
        Long gradeId,
        String gradeTitle,
        String language,
        Integer difficulty,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        int timeLimitSeconds,
        List<SessionQuestionResponse> questions
) {
}
