package kz.damulab.lectures;

import java.util.List;

/**
 * Заголовок и лекции одной страницы «предмет + класс».
 * Разделение по классу не позволяет смешивать, например, материалы третьего
 * и четвёртого классов в одном длинном списке.
 */
public record StudentLectureSubjectPageView(
        Long subjectId,
        String subjectTitleRu,
        String subjectTitleKk,
        Long gradeId,
        String gradeTitleRu,
        String gradeTitleKk,
        List<StudentLectureListItemView> lectures
) {
}
