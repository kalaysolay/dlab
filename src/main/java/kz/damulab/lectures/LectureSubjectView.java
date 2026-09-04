package kz.damulab.lectures;

/** Плитка предмета, для которого существует хотя бы одна опубликованная лекция. */
public record LectureSubjectView(
        Long id,
        String code,
        String titleRu,
        String titleKk,
        long lectureCount
) {
}
