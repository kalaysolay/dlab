package kz.damulab.content;

import java.time.OffsetDateTime;
import java.util.List;

import kz.damulab.questions.ChoiceOptionForm;
import kz.damulab.questions.FillAnswerForm;
import kz.damulab.questions.MatchingPairForm;

/**
 * Представление эталона для UI (список и редактор).
 *
 * <p>Ключ ответа уже распарсен из {@code answer_key_json} в типизированные списки
 * ({@link #options}/{@link #matchingPairs}/{@link #fillAnswers}) — шаблонам не нужно
 * разбирать JSON. Заполнен только список, соответствующий {@link #questionType}.
 */
public record TopicAiExampleResponse(
        Long id,
        Long topicId,
        String questionType,
        int difficulty,
        String bodyRu,
        String bodyKk,
        boolean includeInAi,
        String internalNote,
        List<ChoiceOptionForm> options,
        List<MatchingPairForm> matchingPairs,
        List<FillAnswerForm> fillAnswers,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
