package kz.damulab.lectures;

import java.util.List;

import kz.damulab.testing.ChoiceDisplay;
import kz.damulab.testing.MatchingDisplay;

/**
 * Безопасная модель контрольного вопроса для HTML-формы лекции.
 * Правильный ответ намеренно не передаётся в браузер.
 */
public record LectureCheckpointQuestionView(
        Long id,
        int orderNo,
        String type,
        String body,
        String topicTitle,
        List<ChoiceDisplay> options,
        List<MatchingDisplay> matchingLeft,
        List<MatchingDisplay> matchingRight,
        List<String> fillPlaceholders
) {
}
