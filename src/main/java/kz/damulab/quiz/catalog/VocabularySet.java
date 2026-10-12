package kz.damulab.quiz.catalog;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;

/** Направление перевода стабильно; метаданные набора версионируются. Связи без cascade REMOVE сохраняют историю. */
@Entity
@Table(name = "vocabulary_sets")
public class VocabularySet extends QuizEditorialRecord {
    @Column(name = "code", length = 96, nullable = false)
    private String code;
    @Column(name = "source_language", length = 8, nullable = false)
    private String sourceLanguage;
    @Column(name = "target_language", length = 8, nullable = false)
    private String targetLanguage;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_version_id")
    private VocabularySetVersion publishedVersion;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_version_id")
    private VocabularySetVersion draftVersion;

    protected VocabularySet() {}

    /** Создаёт ещё не сохранённую запись; полноту публикации проверяет редакционный сервис. */
    public VocabularySet(String code, String sourceLanguage, String targetLanguage) {
        this.code = code;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
    }

    /** Возвращает поле code. */
    public String getCode() { return code; }

    /** Возвращает поле source_language. */
    public String getSourceLanguage() { return sourceLanguage; }

    /** Возвращает поле target_language. */
    public String getTargetLanguage() { return targetLanguage; }

    /** Возвращает поле published_version_id. */
    public VocabularySetVersion getPublishedVersion() { return publishedVersion; }
    /** Задаёт published_version_id для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setPublishedVersion(VocabularySetVersion value) { publishedVersion = value; }

    /** Возвращает поле draft_version_id. */
    public VocabularySetVersion getDraftVersion() { return draftVersion; }
    /** Задаёт draft_version_id для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setDraftVersion(VocabularySetVersion value) { draftVersion = value; }
}
