package kz.damulab.quiz.catalog;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;

/** Редакция значения слова с нормализованным поисковым ключом и явной аудиторией. Связи без cascade REMOVE сохраняют историю. */
@Entity
@Table(name = "vocabulary_item_versions")
public class VocabularyItemVersion extends QuizVersionRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private VocabularyItem item;
    @Column(name = "source_text", length = 512, nullable = false)
    private String sourceText;
    @Column(name = "source_normalized", length = 512, nullable = false)
    private String sourceNormalized;
    @Column(name = "pronunciation", length = 255)
    private String pronunciation;
    @Column(name = "part_of_speech", length = 64)
    private String partOfSpeech;
    @Column(name = "meaning_note_ru", columnDefinition = "text")
    private String meaningNoteRu;
    @Column(name = "meaning_note_kk", columnDefinition = "text")
    private String meaningNoteKk;
    @Column(name = "difficulty", nullable = false)
    private int difficulty = 1;
    @Column(name = "source_reference", columnDefinition = "text", nullable = false)
    private String sourceReference;
    @Column(name = "universal_audience", nullable = false)
    private boolean universalAudience = false;
    @ElementCollection
    @CollectionTable(name = "vocabulary_item_grades", joinColumns = @JoinColumn(name = "item_version_id"))
    @Column(name = "grade_id", nullable = false)
    private Set<Long> gradeIds = new LinkedHashSet<>();

    protected VocabularyItemVersion() {}

    /** Создаёт ещё не сохранённую запись; полноту публикации проверяет редакционный сервис. */
    public VocabularyItemVersion(int versionNo, VocabularyItem item, String sourceText, String sourceNormalized, String sourceReference) {
        super(versionNo);
        this.item = item;
        this.sourceText = sourceText;
        this.sourceNormalized = sourceNormalized;
        this.sourceReference = sourceReference;
    }

    /** Возвращает поле item_id. */
    public VocabularyItem getItem() { return item; }

    /** Возвращает поле source_text. */
    public String getSourceText() { return sourceText; }
    /** Задаёт source_text для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setSourceText(String value) { sourceText = value; }

    /** Возвращает поле source_normalized. */
    public String getSourceNormalized() { return sourceNormalized; }
    /** Задаёт source_normalized для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setSourceNormalized(String value) { sourceNormalized = value; }

    /** Возвращает поле pronunciation. */
    public String getPronunciation() { return pronunciation; }
    /** Задаёт pronunciation для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setPronunciation(String value) { pronunciation = value; }

    /** Возвращает поле part_of_speech. */
    public String getPartOfSpeech() { return partOfSpeech; }
    /** Задаёт part_of_speech для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setPartOfSpeech(String value) { partOfSpeech = value; }

    /** Возвращает поле meaning_note_ru. */
    public String getMeaningNoteRu() { return meaningNoteRu; }
    /** Задаёт meaning_note_ru для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setMeaningNoteRu(String value) { meaningNoteRu = value; }

    /** Возвращает поле meaning_note_kk. */
    public String getMeaningNoteKk() { return meaningNoteKk; }
    /** Задаёт meaning_note_kk для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setMeaningNoteKk(String value) { meaningNoteKk = value; }

    /** Возвращает поле difficulty. */
    public int getDifficulty() { return difficulty; }
    /** Задаёт difficulty для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setDifficulty(int value) { difficulty = value; }

    /** Возвращает поле source_reference. */
    public String getSourceReference() { return sourceReference; }
    /** Задаёт source_reference для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setSourceReference(String value) { sourceReference = value; }

    /** Возвращает поле universal_audience. */
    public boolean isUniversalAudience() { return universalAudience; }
    /** Задаёт universal_audience для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setUniversalAudience(boolean value) { universalAudience = value; }

    /** Возвращает классы версии; пустой набор не означает universal. */
    public Set<Long> getGradeIds() { return Collections.unmodifiableSet(gradeIds); }
    /** Задаёт классы версии; существование grade_id проверяет FK. */
    public void setGradeIds(Set<Long> value) { gradeIds = new LinkedHashSet<>(value); }
}
