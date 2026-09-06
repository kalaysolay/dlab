package kz.damulab.testing;

import java.util.List;

import kz.damulab.config.QuestionSelectionStrategy;
import kz.damulab.questions.QuestionVersion;

/** Зафиксированный результат работы селектора и его диагностическая разбивка. */
record QuestionSelectionResult(
        List<QuestionVersion> questions,
        QuestionSelectionStrategy strategy,
        int weakQuestions,
        int watchQuestions,
        int unseenQuestions,
        int strongQuestions
) {

    QuestionSelectionResult {
        questions = List.copyOf(questions);
    }
}
