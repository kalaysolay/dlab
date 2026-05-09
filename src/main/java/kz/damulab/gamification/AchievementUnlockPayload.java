package kz.damulab.gamification;

/**
 * Одно только что полученное достижение после полезной активности (например, первый finish теста).
 * <p>
 * Уходит клиенту в JSON ответа finish-сессии и во flash-сообщении HTML-перенаправления —
 * только для локального показа in-app toast, не смешиваем с модулем admin push/outbox.</p>
 */
public record AchievementUnlockPayload(
        String code,
        String title,
        String description
) implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
}
