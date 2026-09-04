package kz.damulab.lectures;

/** Плитка предмета, для которого существует хотя бы одна опубликованная лекция. */
public record LectureSubjectView(
        Long id,
        String code,
        String titleRu,
        String titleKk,
        String iconStorageKey,
        long lectureCount
) {
    /** Возвращает публичный путь к иконке или {@code null}, если она не задана. */
    public String iconUrl() {
        if (iconStorageKey == null || iconStorageKey.isBlank()) {
            return null;
        }
        return "/files/subject-icons/" + iconStorageKey;
    }
}
