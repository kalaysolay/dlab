package kz.damulab.quiz.catalog;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Неизменяемая запись скрытия/возврата игры; история не зависит от её текущей видимости. */
@Entity
@Table(name = "quiz_game_visibility_events")
public class QuizGameVisibilityEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "game_id", nullable = false) private Long gameId;
    @Column(name = "catalog_visible", nullable = false) private boolean catalogVisible;
    @Column(name = "actor_id") private Long actorId;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    protected QuizGameVisibilityEvent() {}

    /** Записывает новое значение видимости и автора команды без изменения учебной редакции. */
    public QuizGameVisibilityEvent(Long gameId, boolean visible, Long actorId) {
        this.gameId = gameId; this.catalogVisible = visible; this.actorId = actorId;
    }
    /** Возвращает ID события. */
    public Long getId() { return id; }
    /** Возвращает стабильную игру. */
    public Long getGameId() { return gameId; }
    /** Возвращает установленную видимость. */
    public boolean isCatalogVisible() { return catalogVisible; }
    /** Возвращает администратора, выполнившего действие. */
    public Long getActorId() { return actorId; }
    /** Возвращает время действия. */
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
