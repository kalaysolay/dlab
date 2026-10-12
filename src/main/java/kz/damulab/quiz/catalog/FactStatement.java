package kz.damulab.quiz.catalog;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;

/** Стабильное утверждение с отдельными указателями на публикацию и черновик. Связи без cascade REMOVE сохраняют историю. */
@Entity
@Table(name = "fact_statements")
public class FactStatement extends QuizEditorialRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "set_id", nullable = false)
    private FactSet set;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_version_id")
    private FactStatementVersion publishedVersion;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_version_id")
    private FactStatementVersion draftVersion;

    protected FactStatement() {}

    /** Создаёт ещё не сохранённую запись; полноту публикации проверяет редакционный сервис. */
    public FactStatement(FactSet set) {
        this.set = set;
    }

    /** Возвращает поле set_id. */
    public FactSet getSet() { return set; }

    /** Возвращает поле published_version_id. */
    public FactStatementVersion getPublishedVersion() { return publishedVersion; }
    /** Задаёт published_version_id для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setPublishedVersion(FactStatementVersion value) { publishedVersion = value; }

    /** Возвращает поле draft_version_id. */
    public FactStatementVersion getDraftVersion() { return draftVersion; }
    /** Задаёт draft_version_id для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setDraftVersion(FactStatementVersion value) { draftVersion = value; }
}
