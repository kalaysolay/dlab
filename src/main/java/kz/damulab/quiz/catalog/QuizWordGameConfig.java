package kz.damulab.quiz.catalog;

import jakarta.persistence.*;

/** Типовой источник версии WORD_TRANSLATION. Составной FK в БД запрещает источник другой механики. */
@Entity @Table(name = "quiz_word_game_configs")
public class QuizWordGameConfig {
    @Id @Column(name = "game_version_id")
    private Long gameVersionId;
    @MapsId @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "game_version_id")
    private QuizGameVersion gameVersion;
    @Enumerated(EnumType.STRING) @Column(name = "game_type", nullable = false, length = 32)
    private QuizGameType gameType = QuizGameType.WORD_TRANSLATION;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vocabulary_set_id")
    private VocabularySet vocabularySet;
    @Column(name = "source_language", length = 8)
    private String sourceLanguage;
    @Column(name = "target_language", length = 8)
    private String targetLanguage;
    protected QuizWordGameConfig() {}
    /** Связывает несохранённую конфигурацию с версией; тип и направление дополнительно проверяет FK. */
    public QuizWordGameConfig(QuizGameVersion gameVersion, VocabularySet vocabularySet, String sourceLanguage, String targetLanguage) {
        this.gameVersion = gameVersion;
        this.vocabularySet = vocabularySet;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
    }
    /** Возвращает идентификатор версии — одновременно PK конфигурации. */
    public Long getGameVersionId() { return gameVersionId; }
    /** Возвращает версию без неявного удаления или публикации. */
    public QuizGameVersion getGameVersion() { return gameVersion; }
    /** Возвращает настройку источника vocabularySet. */
    public VocabularySet getVocabularySet() { return vocabularySet; }
    /** Возвращает настройку источника sourceLanguage. */
    public String getSourceLanguage() { return sourceLanguage; }
    /** Возвращает настройку источника targetLanguage. */
    public String getTargetLanguage() { return targetLanguage; }
}
