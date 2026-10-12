package kz.damulab.quiz.catalog;

import jakarta.persistence.*;

/** Типовой источник версии TRUE_FALSE. Составной FK в БД запрещает источник другой механики. */
@Entity @Table(name = "quiz_fact_game_configs")
public class QuizFactGameConfig {
    @Id @Column(name = "game_version_id")
    private Long gameVersionId;
    @MapsId @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "game_version_id")
    private QuizGameVersion gameVersion;
    @Enumerated(EnumType.STRING) @Column(name = "game_type", nullable = false, length = 32)
    private QuizGameType gameType = QuizGameType.TRUE_FALSE;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "fact_set_id")
    private FactSet factSet;
    protected QuizFactGameConfig() {}
    /** Связывает несохранённую конфигурацию с версией; тип и направление дополнительно проверяет FK. */
    public QuizFactGameConfig(QuizGameVersion gameVersion, FactSet factSet) {
        this.gameVersion = gameVersion;
        this.factSet = factSet;
    }
    /** Возвращает идентификатор версии — одновременно PK конфигурации. */
    public Long getGameVersionId() { return gameVersionId; }
    /** Возвращает версию без неявного удаления или публикации. */
    public QuizGameVersion getGameVersion() { return gameVersion; }
    /** Возвращает настройку источника factSet. */
    public FactSet getFactSet() { return factSet; }
}
