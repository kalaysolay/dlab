package kz.damulab.quiz.catalog;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Общий идентификатор и аудит новых банков. Удаление авторов не удаляет учебную историю. */
@MappedSuperclass
public abstract class QuizAuditRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "created_by")
    private Long createdBy;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    protected void touch() { updatedAt = OffsetDateTime.now(); }
    /** Возвращает идентификатор записи после сохранения. */
    public Long getId() { return id; }
    /** Возвращает автора; null допускается для системных данных. */
    public Long getCreatedBy() { return createdBy; }
    /** Фиксирует автора; существование пользователя проверяет FK базы. */
    public void setCreatedBy(Long value) { createdBy = value; }
    /** Возвращает время создания. */
    public OffsetDateTime getCreatedAt() { return createdAt; }
    /** Возвращает время последнего изменения. */
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
