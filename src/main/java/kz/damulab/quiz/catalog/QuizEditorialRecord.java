package kz.damulab.quiz.catalog;

import jakarta.persistence.*;

/** Редакционный статус стабильного объекта или версии. Публикационный workflow реализуется отдельным сервисом. */
@MappedSuperclass
public abstract class QuizEditorialRecord extends QuizAuditRecord {
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private EditorialStatus status = EditorialStatus.DRAFT;
    /** Возвращает статус, не подменяя им published/draft указатели. */
    public EditorialStatus getStatus() { return status; }
    /** Изменяет статус в транзакции будущего редакционного сервиса, не публикует автоматически. */
    public void setStatus(EditorialStatus value) { status = java.util.Objects.requireNonNull(value); }
}
