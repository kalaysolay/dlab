package kz.damulab.lectures;

import java.util.List;

/** Результат атомарного batch-импорта с соответствием внешнего и внутреннего ID. */
public record LectureImportResponse(
        int importedCount,
        List<ImportedLesson> lessons
) {

    public record ImportedLesson(
            String externalId,
            Long lectureId,
            String status
    ) {
    }
}
