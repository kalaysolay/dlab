package kz.damulab.content;

/** Данные предмета для административного списка и формы. */
public record AdminSubjectView(
        Long id,
        String code,
        String titleRu,
        String titleKk,
        String descriptionRu,
        String descriptionKk,
        String iconUrl,
        long topicCount
) {
    /** Предмет можно удалить только тогда, когда у него нет тем. */
    public boolean canDelete() {
        return topicCount == 0;
    }
}
