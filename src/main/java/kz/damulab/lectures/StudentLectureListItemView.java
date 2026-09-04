package kz.damulab.lectures;

import java.time.OffsetDateTime;

/** Строка лекции на странице выбранного предмета. */
public record StudentLectureListItemView(
        Long id,
        String titleRu,
        String titleKk,
        String topicTitleRu,
        String topicTitleKk,
        OffsetDateTime createdAt,
        String progressStatus
) {
}
