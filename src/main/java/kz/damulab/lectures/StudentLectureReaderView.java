package kz.damulab.lectures;

import java.util.List;

/** Полная модель страницы чтения лекции для текущего ученика. */
public record StudentLectureReaderView(
        LectureResponse lecture,
        Long subjectId,
        String subjectTitleRu,
        String subjectTitleKk,
        String progressStatus,
        boolean checkpointPassed,
        boolean canComplete,
        List<LectureCheckpointQuestionView> questions
) {
}
