package kz.damulab.speaking;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Проверяемая заглушка без сети. Не выдаёт шаблонные ответы за полноценную языковую диагностику. */
@Component
class StubSpeakingTutor implements SpeakingTutor {
    private static final Pattern PAST_EXAMPLE = Pattern.compile(
            "Yesterday I go to the cinema\\.(?: I watched an interesting film\\.)?");

    /** Исправляет только заранее проверенный пример, остальные фразы не объявляет правильными или ошибочными. */
    @Override
    public Reply answer(SpeakingScenario scenario, int acceptedTurns, String text, String language) {
        // Полное совпадение защищает от ошибочного разбора цитаты внутри произвольной фразы.
        var feedback = PAST_EXAMPLE.matcher(text).matches()
                ? List.of(new Feedback("grammar.past_simple", "Yesterday I go", "Yesterday I went",
                    "kk".equals(language) ? "Өткен шақта go орнына went қолданылады."
                            : "Речь о прошлом: после yesterday здесь нужна форма went."))
                : List.<Feedback>of();
        String next = acceptedTurns < scenario.questions().size() ? scenario.questions().get(acceptedTurns) : "";
        return new Reply("Thanks for sharing.", next, feedback);
    }
}
