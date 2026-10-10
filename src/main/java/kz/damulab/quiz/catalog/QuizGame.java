package kz.damulab.quiz.catalog;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;

/** Стабильная игра: опубликованная и черновая редакции независимы от видимости. Связи без cascade REMOVE сохраняют историю. */
@Entity
@Table(name = "quiz_games")
public class QuizGame extends QuizEditorialRecord {
    @Column(name = "code", length = 96, nullable = false)
    private String code;
    @Column(name = "catalog_visible", nullable = false)
    private boolean catalogVisible = true;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_version_id")
    private QuizGameVersion publishedVersion;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_version_id")
    private QuizGameVersion draftVersion;
    @Column(name = "visibility_updated_by")
    private Long visibilityUpdatedBy;
    @Column(name = "visibility_updated_at")
    private OffsetDateTime visibilityUpdatedAt;
    @Column(name = "admin_revision", nullable = false)
    private long adminRevision;

    protected QuizGame() {}

    /** Создаёт ещё не сохранённую запись; полноту публикации проверяет редакционный сервис. */
    public QuizGame(String code) {
        this.code = code;
    }

    /** Возвращает поле code. */
    public String getCode() { return code; }

    /** Меняет код только до первой публикации; прежние опубликованные URL остаются стабильными. */
    public void changeDraftCode(String value) {
        if (publishedVersion != null && !code.equals(value)) throw new ContentValidationException("Код опубликованной игры неизменяем");
        code = value;
    }

    /** Возвращает ревизию для защиты POST-команд от устаревшей формы. */
    public long getAdminRevision() { return adminRevision; }

    /** Увеличивает ревизию под блокировкой стабильной игры после успешной команды. */
    public void advanceAdminRevision() { adminRevision++; }

    /** Возвращает поле catalog_visible. */
    public boolean isCatalogVisible() { return catalogVisible; }
    /** Задаёт catalog_visible для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setCatalogVisible(boolean value) { catalogVisible = value; }

    /** Возвращает поле published_version_id. */
    public QuizGameVersion getPublishedVersion() { return publishedVersion; }
    /** Задаёт published_version_id для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setPublishedVersion(QuizGameVersion value) { publishedVersion = value; }

    /** Возвращает поле draft_version_id. */
    public QuizGameVersion getDraftVersion() { return draftVersion; }
    /** Задаёт draft_version_id для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setDraftVersion(QuizGameVersion value) { draftVersion = value; }

    /** Возвращает поле visibility_updated_by. */
    public Long getVisibilityUpdatedBy() { return visibilityUpdatedBy; }
    /** Задаёт visibility_updated_by для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setVisibilityUpdatedBy(Long value) { visibilityUpdatedBy = value; }

    /** Возвращает поле visibility_updated_at. */
    public OffsetDateTime getVisibilityUpdatedAt() { return visibilityUpdatedAt; }
    /** Задаёт visibility_updated_at для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setVisibilityUpdatedAt(OffsetDateTime value) { visibilityUpdatedAt = value; }
}
