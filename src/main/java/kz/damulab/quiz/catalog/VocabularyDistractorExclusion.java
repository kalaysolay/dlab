package kz.damulab.quiz.catalog;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;

/** Явное несовместимое значение для дистрактора; выборка учитывает обе стороны исключения. Связи без cascade REMOVE сохраняют историю. */
@Entity
@Table(name = "vocabulary_distractor_exclusions")
public class VocabularyDistractorExclusion extends QuizAuditRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_version_id", nullable = false)
    private VocabularyItemVersion itemVersion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "excluded_item_version_id", nullable = false)
    private VocabularyItemVersion excludedItemVersion;
    @Column(name = "reason", columnDefinition = "text", nullable = false)
    private String reason;

    protected VocabularyDistractorExclusion() {}

    /** Создаёт ещё не сохранённую запись; полноту публикации проверяет редакционный сервис. */
    public VocabularyDistractorExclusion(VocabularyItemVersion itemVersion, VocabularyItemVersion excludedItemVersion, String reason) {
        this.itemVersion = itemVersion;
        this.excludedItemVersion = excludedItemVersion;
        this.reason = reason;
    }

    /** Возвращает поле item_version_id. */
    public VocabularyItemVersion getItemVersion() { return itemVersion; }

    /** Возвращает поле excluded_item_version_id. */
    public VocabularyItemVersion getExcludedItemVersion() { return excludedItemVersion; }

    /** Возвращает поле reason. */
    public String getReason() { return reason; }
    /** Задаёт reason для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setReason(String value) { reason = value; }
}
