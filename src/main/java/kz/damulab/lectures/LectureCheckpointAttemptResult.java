package kz.damulab.lectures;

/** Результат одной попытки ответить на контрольные вопросы лекции. */
public record LectureCheckpointAttemptResult(
        boolean passed,
        int correctCount,
        int totalCount
) {
}
