package kz.damulab.content;

import java.util.List;

/**
 * Строка таблицы покрытия для хаба «Эталоны для ИИ».
 *
 * <p>Показывает по теме: сколько всего эталонов, сколько активных ({@code include_in_ai})
 * и какие типы вопросов уже покрыты. По этим данным методист видит, где few-shot есть,
 * а где тема идёт в генерацию «пустой» (только по названию).
 *
 * @param total     всего эталонов у темы
 * @param active    из них включённых в AI
 * @param typesActive типы вопросов, по которым есть активный эталон (SCQ/MCQ/MATCHING/FILL_IN)
 */
public record TopicAiExampleCoverageResponse(
        Long topicId,
        String titleRu,
        String titleKk,
        long total,
        long active,
        List<String> typesActive
) {
}
