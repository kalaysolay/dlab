package kz.damulab.questions;

/**
 * @param stubMode {@code true}, если сработал намеренный режим {@code damulab.ai.provider=stub} (без вызова внешнего LLM).
 *         При ошибке реального провайдера ответа нет — клиент получит HTTP с телом {@code error} + {@code message}.
 */
public record MiniLectureDraftResponse(
        String explanationRu,
        String explanationKk,
        String miniLectureRu,
        String miniLectureKk,
        String correctAnswerRu,
        String correctAnswerKk,
        boolean stubMode
) {
}
