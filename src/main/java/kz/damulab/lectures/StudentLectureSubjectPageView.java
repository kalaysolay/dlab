package kz.damulab.lectures;

import java.util.List;

/** Заголовок предмета и отсортированные лекции для одной страницы каталога. */
public record StudentLectureSubjectPageView(
        Long subjectId,
        String subjectTitleRu,
        String subjectTitleKk,
        List<StudentLectureListItemView> lectures
) {
}
