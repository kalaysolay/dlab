package kz.damulab.quiz.catalog;

import jakarta.persistence.*;
/** Перевод версии слова. Внутренний marker обеспечивает максимум один primary, не редактируется извне. */
@Entity @Table(name = "vocabulary_item_answers")
public class VocabularyItemAnswer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "item_version_id") private VocabularyItemVersion itemVersion;
    @Column(name = "answer_text", length = 512) private String answerText;
    @Column(name = "answer_normalized", length = 512) private String answerNormalized;
    @Column(name = "is_primary", nullable = false) private boolean primary;
    @Column(name = "primary_marker") private Integer primaryMarker;
    protected VocabularyItemAnswer() {}
    /** Создаёт перевод; нормализация и полнота primary проверяются редакционным сервисом. */
    public VocabularyItemAnswer(VocabularyItemVersion itemVersion, String text, String normalized, boolean primary) {
        this.itemVersion = itemVersion; this.answerText = text; this.answerNormalized = normalized;
        this.primary = primary; this.primaryMarker = primary ? 1 : null;
    }
    /** Возвращает ID перевода. */
    public Long getId() { return id; }
    /** Возвращает владельца перевода. */
    public VocabularyItemVersion getItemVersion() { return itemVersion; }
    /** Возвращает отображаемый перевод, не поисковую нормализацию. */
    public String getAnswerText() { return answerText; }
    /** Возвращает ключ сравнения перевода. */
    public String getAnswerNormalized() { return answerNormalized; }
    /** Возвращает признак основного варианта. */
    public boolean isPrimary() { return primary; }
}
