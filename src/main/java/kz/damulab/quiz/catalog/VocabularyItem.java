package kz.damulab.quiz.catalog;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;

/** Учебное значение слова, а не уникальная строка: омонимы имеют самостоятельные версии. Связи без cascade REMOVE сохраняют историю. */
@Entity
@Table(name = "vocabulary_items")
public class VocabularyItem extends QuizEditorialRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "set_id", nullable = false)
    private VocabularySet set;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_version_id")
    private VocabularyItemVersion publishedVersion;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_version_id")
    private VocabularyItemVersion draftVersion;

    protected VocabularyItem() {}

    /** Создаёт ещё не сохранённую запись; полноту публикации проверяет редакционный сервис. */
    public VocabularyItem(VocabularySet set) {
        this.set = set;
    }

    /** Возвращает поле set_id. */
    public VocabularySet getSet() { return set; }

    /** Возвращает поле published_version_id. */
    public VocabularyItemVersion getPublishedVersion() { return publishedVersion; }
    /** Задаёт published_version_id для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setPublishedVersion(VocabularyItemVersion value) { publishedVersion = value; }

    /** Возвращает поле draft_version_id. */
    public VocabularyItemVersion getDraftVersion() { return draftVersion; }
    /** Задаёт draft_version_id для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setDraftVersion(VocabularyItemVersion value) { draftVersion = value; }
}
