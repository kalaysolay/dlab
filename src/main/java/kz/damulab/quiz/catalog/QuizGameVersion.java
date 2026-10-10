package kz.damulab.quiz.catalog;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;

/** Версия карточки и общих настроек. Типовые источники лежат в отдельных config-таблицах. Связи без cascade REMOVE сохраняют историю. */
@Entity
@Table(name = "quiz_game_versions")
public class QuizGameVersion extends QuizVersionRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private QuizGame game;
    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", length = 32, nullable = false)
    private QuizGameType gameType;
    @Column(name = "title_ru", length = 255, nullable = false)
    private String titleRu;
    @Column(name = "title_kk", length = 255, nullable = false)
    private String titleKk;
    @Column(name = "description_ru", columnDefinition = "text")
    private String descriptionRu;
    @Column(name = "description_kk", columnDefinition = "text")
    private String descriptionKk;
    @Column(name = "mechanic_label_ru", length = 128)
    private String mechanicLabelRu;
    @Column(name = "mechanic_label_kk", length = 128)
    private String mechanicLabelKk;
    @Column(name = "card_style", length = 32, nullable = false)
    private String cardStyle = "default";
    @Column(name = "catalog_order", nullable = false)
    private int catalogOrder = 0;
    @Enumerated(EnumType.STRING)
    @Column(name = "grade_selection_mode", length = 32, nullable = false)
    private GradeSelectionMode gradeSelectionMode = GradeSelectionMode.NONE;
    @Enumerated(EnumType.STRING)
    @Column(name = "audience_policy", length = 32, nullable = false)
    private AudiencePolicy audiencePolicy = AudiencePolicy.UNIVERSAL;
    @Column(name = "default_question_count", nullable = false)
    private int defaultQuestionCount = 10;
    @Column(name = "min_question_count", nullable = false)
    private int minQuestionCount = 1;
    @Column(name = "max_question_count", nullable = false)
    private int maxQuestionCount = 20;
    @Column(name = "default_round_seconds", nullable = false)
    private int defaultRoundSeconds = 20;
    @Column(name = "min_round_seconds", nullable = false)
    private int minRoundSeconds = 5;
    @Column(name = "max_round_seconds", nullable = false)
    private int maxRoundSeconds = 120;
    @Column(name = "max_players", nullable = false)
    private int maxPlayers = 4;
    @Column(name = "question_count_editable", nullable = false)
    private boolean questionCountEditable = false;
    @Column(name = "round_seconds_editable", nullable = false)
    private boolean roundSecondsEditable = false;
    @ElementCollection
    @CollectionTable(name = "quiz_game_version_grades", joinColumns = @JoinColumn(name = "game_version_id"))
    @Column(name = "grade_id", nullable = false)
    private Set<Long> gradeIds = new LinkedHashSet<>();

    protected QuizGameVersion() {}

    /** Создаёт ещё не сохранённую запись; полноту публикации проверяет редакционный сервис. */
    public QuizGameVersion(int versionNo, QuizGame game, QuizGameType gameType, String titleRu, String titleKk) {
        super(versionNo);
        this.game = game;
        this.gameType = gameType;
        this.titleRu = titleRu;
        this.titleKk = titleKk;
    }

    /** Возвращает поле game_id. */
    public QuizGame getGame() { return game; }

    /** Возвращает поле game_type. */
    public QuizGameType getGameType() { return gameType; }

    /** Возвращает поле title_ru. */
    public String getTitleRu() { return titleRu; }
    /** Задаёт title_ru для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setTitleRu(String value) { titleRu = value; }

    /** Возвращает поле title_kk. */
    public String getTitleKk() { return titleKk; }
    /** Задаёт title_kk для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setTitleKk(String value) { titleKk = value; }

    /** Возвращает поле description_ru. */
    public String getDescriptionRu() { return descriptionRu; }
    /** Задаёт description_ru для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setDescriptionRu(String value) { descriptionRu = value; }

    /** Возвращает поле description_kk. */
    public String getDescriptionKk() { return descriptionKk; }
    /** Задаёт description_kk для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setDescriptionKk(String value) { descriptionKk = value; }

    /** Возвращает поле mechanic_label_ru. */
    public String getMechanicLabelRu() { return mechanicLabelRu; }
    /** Задаёт mechanic_label_ru для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setMechanicLabelRu(String value) { mechanicLabelRu = value; }

    /** Возвращает поле mechanic_label_kk. */
    public String getMechanicLabelKk() { return mechanicLabelKk; }
    /** Задаёт mechanic_label_kk для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setMechanicLabelKk(String value) { mechanicLabelKk = value; }

    /** Возвращает поле card_style. */
    public String getCardStyle() { return cardStyle; }
    /** Задаёт card_style для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setCardStyle(String value) { cardStyle = value; }

    /** Возвращает поле catalog_order. */
    public int getCatalogOrder() { return catalogOrder; }
    /** Задаёт catalog_order для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setCatalogOrder(int value) { catalogOrder = value; }

    /** Возвращает поле grade_selection_mode. */
    public GradeSelectionMode getGradeSelectionMode() { return gradeSelectionMode; }
    /** Задаёт grade_selection_mode для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setGradeSelectionMode(GradeSelectionMode value) { gradeSelectionMode = value; }

    /** Возвращает поле audience_policy. */
    public AudiencePolicy getAudiencePolicy() { return audiencePolicy; }
    /** Задаёт audience_policy для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setAudiencePolicy(AudiencePolicy value) { audiencePolicy = value; }

    /** Возвращает поле default_question_count. */
    public int getDefaultQuestionCount() { return defaultQuestionCount; }
    /** Задаёт default_question_count для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setDefaultQuestionCount(int value) { defaultQuestionCount = value; }

    /** Возвращает поле min_question_count. */
    public int getMinQuestionCount() { return minQuestionCount; }
    /** Задаёт min_question_count для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setMinQuestionCount(int value) { minQuestionCount = value; }

    /** Возвращает поле max_question_count. */
    public int getMaxQuestionCount() { return maxQuestionCount; }
    /** Задаёт max_question_count для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setMaxQuestionCount(int value) { maxQuestionCount = value; }

    /** Возвращает поле default_round_seconds. */
    public int getDefaultRoundSeconds() { return defaultRoundSeconds; }
    /** Задаёт default_round_seconds для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setDefaultRoundSeconds(int value) { defaultRoundSeconds = value; }

    /** Возвращает поле min_round_seconds. */
    public int getMinRoundSeconds() { return minRoundSeconds; }
    /** Задаёт min_round_seconds для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setMinRoundSeconds(int value) { minRoundSeconds = value; }

    /** Возвращает поле max_round_seconds. */
    public int getMaxRoundSeconds() { return maxRoundSeconds; }
    /** Задаёт max_round_seconds для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setMaxRoundSeconds(int value) { maxRoundSeconds = value; }

    /** Возвращает поле max_players. */
    public int getMaxPlayers() { return maxPlayers; }
    /** Задаёт max_players для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setMaxPlayers(int value) { maxPlayers = value; }

    /** Возвращает поле question_count_editable. */
    public boolean isQuestionCountEditable() { return questionCountEditable; }
    /** Задаёт question_count_editable для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setQuestionCountEditable(boolean value) { questionCountEditable = value; }

    /** Возвращает поле round_seconds_editable. */
    public boolean isRoundSecondsEditable() { return roundSecondsEditable; }
    /** Задаёт round_seconds_editable для записи в транзакции сервиса; ограничения владельца проверяет БД. */
    public void setRoundSecondsEditable(boolean value) { roundSecondsEditable = value; }

    /** Возвращает разрешённые классы игры; коллекция доступна только для чтения. */
    public Set<Long> getGradeIds() { return Collections.unmodifiableSet(gradeIds); }
    /** Задаёт разрешённые классы; существование grade_id проверяет FK. */
    public void setGradeIds(Set<Long> value) { gradeIds = new LinkedHashSet<>(value); }
}
