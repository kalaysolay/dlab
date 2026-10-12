package kz.damulab.quiz;

import java.time.OffsetDateTime;
import java.util.List;
import kz.damulab.quiz.engine.QuizCurrentRoundReader.CurrentRound;

/** Безопасный расширенный контракт комнаты: прежние имена полей сохраняются, сущности и закрытые снимки не выдаются. */
public record QuizGameRoomResponse(Long id, String code, String status, boolean host, Long currentParticipantId,
        Long subjectId, String subjectTitle, Long gradeId, String gradeTitle, String language, Integer difficulty,
        int questionCount, int roundSeconds, int maxPlayers, OffsetDateTime serverTime, Long activeRoundId,
        OffsetDateTime createdAt, OffsetDateTime startedAt, OffsetDateTime finishedAt,
        List<Member> participants, List<Window> rounds, Long gameId, String gameCode, String gameType,
        String gameTitle, String gameDescription, boolean currentParticipantHost, boolean canStart,
        List<String> startBlockers, boolean canInvite, OffsetDateTime expiresAt, long settingsRevision,
        CurrentRound currentRound, String contentKind, Integer audienceGradeNo, String gradeSelectionMode,
        String audiencePolicy, boolean questionCountEditable, boolean roundSecondsEditable,
        int minQuestionCount, int maxQuestionCount, int minRoundSeconds, int maxRoundSeconds) {
    /** Общий список участников не содержит аккаунт, профиль или чужие ответы. */
    public record Member(Long id, String displayName, boolean ready, boolean host, boolean current) {}
    /** Будущие окна содержат только порядок и серверные границы, без условий и вариантов. */
    public record Window(int orderNo, OffsetDateTime startsAt, OffsetDateTime endsAt) {}
    /** До join разрешены только название, организатор, заполненность и доступность входа. */
    public record Preview(String code, String gameTitle, String organizer, int participantCount,
                          int maxPlayers, String status, boolean canJoin, String joinBlocker) {}
}
