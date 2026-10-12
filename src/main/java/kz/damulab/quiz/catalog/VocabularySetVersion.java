package kz.damulab.quiz.catalog;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;

/** Проверяемые метаданные и политика дистракторов набора; редакция фиксируется раундом. Связи без cascade REMOVE сохраняют историю. */
@Entity
@Table(name = "vocabulary_set_versions")
public class VocabularySetVersion extends QuizVersionRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "set_id", nullable = false)
    private VocabularySet set;
    @Column(name = "title_ru", length = 255, nullable = false)
    private String titleRu;
    @Column(name = "title_kk", length = 255, nullable = false)
    private String titleKk;
    @Column(name = "source_reference", columnDefinition = "text", nullable = false)
    private String sourceReference;
    @Column(name = "cross_set_distractors_allowed", nullable = false)
    private boolean crossSetDistractorsAllowed = false;

    protected VocabularySetVersion() {}

    /** Создаёт ещё не сохранённую запись; полноту публикации проверяет редакционный сервис. */
    public VocabularySetVersion(int versionNo, VocabularySet set, String titleRu, String titleKk, String sourceReference) {
        super(versionNo);
        this.set = set;
        this.titleRu = titleRu;
        this.titleKk = titleKk;
        this.sourceReference = sourceReference;
    }

    /** Возвращает поле set_id. */
    public VocabularySet getSet() { return set; }

    /** Возвращает поле title_ru. */
    public String getTitleRu() { return titleRu; }
    /** Задаёт title_ru для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setTitleRu(String value) { titleRu = value; }

    /** Возвращает поле title_kk. */
    public String getTitleKk() { return titleKk; }
    /** Задаёт title_kk для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setTitleKk(String value) { titleKk = value; }

    /** Возвращает поле source_reference. */
    public String getSourceReference() { return sourceReference; }
    /** Задаёт source_reference для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setSourceReference(String value) { sourceReference = value; }

    /** Возвращает поле cross_set_distractors_allowed. */
    public boolean isCrossSetDistractorsAllowed() { return crossSetDistractorsAllowed; }
    /** Задаёт cross_set_distractors_allowed для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setCrossSetDistractorsAllowed(boolean value) { crossSetDistractorsAllowed = value; }
}
