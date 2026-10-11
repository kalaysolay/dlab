package kz.damulab.quiz;

import com.fasterxml.jackson.databind.JsonNode;

/** Типизированные команды новой комнаты; ключ, банк, тип и вместимость сервер получает из версии игры. */
public final class QuizGameRoomRequest {
    private QuizGameRoomRequest() {}
    /** Параметры create; null count/seconds означают значения закрепляемой версии. */
    public record Create(String gameCode, Long gradeId, Integer questionCount, Integer roundSeconds, String idempotencyKey) {}
    /** Самостоятельная предметная игра: null count/seconds означают 15 вопросов и 10 секунд. */
    public record CustomCreate(Long subjectId, Long gradeId, String language, Integer questionCount, Integer roundSeconds, String idempotencyKey) {}
    /** Подтверждение готовности к конкретной редакции правил. */
    public record Ready(boolean ready, long settingsRevision) {}
    /** Полное изменение разрешённых настроек; expected revision защищает от устаревшей формы. */
    public record Settings(Long gradeId, Integer questionCount, Integer roundSeconds, long settingsRevision) {}
    /** False является ответом; skipped допускается только без содержательного answer. */
    public record Answer(Long roundId, JsonNode answer, boolean skipped) {}
}
