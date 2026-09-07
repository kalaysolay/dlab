package kz.damulab.lectures;

/**
 * Плитка одного раздела ученического каталога: конкретный предмет в конкретном классе.
 * Плитка создаётся запросом только при наличии хотя бы одной опубликованной лекции,
 * поэтому ученик не увидит пустые или состоящие только из черновиков разделы.
 */
public record LectureSubjectGradeView(
        Long subjectId,
        String subjectCode,
        String subjectTitleRu,
        String subjectTitleKk,
        String iconStorageKey,
        Long gradeId,
        Integer gradeNo,
        String gradeTitleRu,
        String gradeTitleKk,
        long lectureCount
) {
    /** Возвращает публичный путь к иконке предмета или {@code null}, если она не задана. */
    public String iconUrl() {
        if (iconStorageKey == null || iconStorageKey.isBlank()) {
            return null;
        }
        return "/files/subject-icons/" + iconStorageKey;
    }
}
