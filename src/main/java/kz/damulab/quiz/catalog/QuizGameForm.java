package kz.damulab.quiz.catalog;

import java.util.*;
import kz.damulab.questions.QuestionType;

/** Типизированная форма черновика игры; публикационные поля аудита не принимаются от браузера. */
public class QuizGameForm {
    private String code = "";
    private QuizGameType gameType = QuizGameType.WORD_TRANSLATION;
    private long expectedRevision = 0;
    private String titleRu = "";
    private String titleKk = "";
    private String descriptionRu = "";
    private String descriptionKk = "";
    private String mechanicLabelRu = "";
    private String mechanicLabelKk = "";
    private String cardStyle = "default";
    private int catalogOrder = 0;
    private GradeSelectionMode gradeSelectionMode = GradeSelectionMode.NONE;
    private AudiencePolicy audiencePolicy = AudiencePolicy.UNIVERSAL;
    private int defaultQuestionCount = 10;
    private int minQuestionCount = 1;
    private int maxQuestionCount = 20;
    private int defaultRoundSeconds = 20;
    private int minRoundSeconds = 5;
    private int maxRoundSeconds = 120;
    private int maxPlayers = 4;
    private boolean questionCountEditable = false;
    private boolean roundSecondsEditable = false;
    private List<Long> gradeIds = new ArrayList<>();
    private Long vocabularySetId = null;
    private String sourceLanguage = "en";
    private String targetLanguage = "ru";
    private Long subjectId = null;
    private Integer difficulty = null;
    private Set<QuestionType> questionTypes = new LinkedHashSet<>(Set.of(QuestionType.SCQ));
    private Long factSetId = null;

    /** Возвращает настройку code из введённой формы. */
    public String getCode() { return code; }
    /** Принимает настройку code; допустимость проверяет сервис до записи в БД. */
    public void setCode(String value) { code = value; }

    /** Возвращает настройку gameType из введённой формы. */
    public QuizGameType getGameType() { return gameType; }
    /** Принимает настройку gameType; допустимость проверяет сервис до записи в БД. */
    public void setGameType(QuizGameType value) { gameType = value; }

    /** Возвращает настройку expectedRevision из введённой формы. */
    public long getExpectedRevision() { return expectedRevision; }
    /** Принимает настройку expectedRevision; допустимость проверяет сервис до записи в БД. */
    public void setExpectedRevision(long value) { expectedRevision = value; }

    /** Возвращает настройку titleRu из введённой формы. */
    public String getTitleRu() { return titleRu; }
    /** Принимает настройку titleRu; допустимость проверяет сервис до записи в БД. */
    public void setTitleRu(String value) { titleRu = value; }

    /** Возвращает настройку titleKk из введённой формы. */
    public String getTitleKk() { return titleKk; }
    /** Принимает настройку titleKk; допустимость проверяет сервис до записи в БД. */
    public void setTitleKk(String value) { titleKk = value; }

    /** Возвращает настройку descriptionRu из введённой формы. */
    public String getDescriptionRu() { return descriptionRu; }
    /** Принимает настройку descriptionRu; допустимость проверяет сервис до записи в БД. */
    public void setDescriptionRu(String value) { descriptionRu = value; }

    /** Возвращает настройку descriptionKk из введённой формы. */
    public String getDescriptionKk() { return descriptionKk; }
    /** Принимает настройку descriptionKk; допустимость проверяет сервис до записи в БД. */
    public void setDescriptionKk(String value) { descriptionKk = value; }

    /** Возвращает настройку mechanicLabelRu из введённой формы. */
    public String getMechanicLabelRu() { return mechanicLabelRu; }
    /** Принимает настройку mechanicLabelRu; допустимость проверяет сервис до записи в БД. */
    public void setMechanicLabelRu(String value) { mechanicLabelRu = value; }

    /** Возвращает настройку mechanicLabelKk из введённой формы. */
    public String getMechanicLabelKk() { return mechanicLabelKk; }
    /** Принимает настройку mechanicLabelKk; допустимость проверяет сервис до записи в БД. */
    public void setMechanicLabelKk(String value) { mechanicLabelKk = value; }

    /** Возвращает настройку cardStyle из введённой формы. */
    public String getCardStyle() { return cardStyle; }
    /** Принимает настройку cardStyle; допустимость проверяет сервис до записи в БД. */
    public void setCardStyle(String value) { cardStyle = value; }

    /** Возвращает настройку catalogOrder из введённой формы. */
    public int getCatalogOrder() { return catalogOrder; }
    /** Принимает настройку catalogOrder; допустимость проверяет сервис до записи в БД. */
    public void setCatalogOrder(int value) { catalogOrder = value; }

    /** Возвращает настройку gradeSelectionMode из введённой формы. */
    public GradeSelectionMode getGradeSelectionMode() { return gradeSelectionMode; }
    /** Принимает настройку gradeSelectionMode; допустимость проверяет сервис до записи в БД. */
    public void setGradeSelectionMode(GradeSelectionMode value) { gradeSelectionMode = value; }

    /** Возвращает настройку audiencePolicy из введённой формы. */
    public AudiencePolicy getAudiencePolicy() { return audiencePolicy; }
    /** Принимает настройку audiencePolicy; допустимость проверяет сервис до записи в БД. */
    public void setAudiencePolicy(AudiencePolicy value) { audiencePolicy = value; }

    /** Возвращает настройку defaultQuestionCount из введённой формы. */
    public int getDefaultQuestionCount() { return defaultQuestionCount; }
    /** Принимает настройку defaultQuestionCount; допустимость проверяет сервис до записи в БД. */
    public void setDefaultQuestionCount(int value) { defaultQuestionCount = value; }

    /** Возвращает настройку minQuestionCount из введённой формы. */
    public int getMinQuestionCount() { return minQuestionCount; }
    /** Принимает настройку minQuestionCount; допустимость проверяет сервис до записи в БД. */
    public void setMinQuestionCount(int value) { minQuestionCount = value; }

    /** Возвращает настройку maxQuestionCount из введённой формы. */
    public int getMaxQuestionCount() { return maxQuestionCount; }
    /** Принимает настройку maxQuestionCount; допустимость проверяет сервис до записи в БД. */
    public void setMaxQuestionCount(int value) { maxQuestionCount = value; }

    /** Возвращает настройку defaultRoundSeconds из введённой формы. */
    public int getDefaultRoundSeconds() { return defaultRoundSeconds; }
    /** Принимает настройку defaultRoundSeconds; допустимость проверяет сервис до записи в БД. */
    public void setDefaultRoundSeconds(int value) { defaultRoundSeconds = value; }

    /** Возвращает настройку minRoundSeconds из введённой формы. */
    public int getMinRoundSeconds() { return minRoundSeconds; }
    /** Принимает настройку minRoundSeconds; допустимость проверяет сервис до записи в БД. */
    public void setMinRoundSeconds(int value) { minRoundSeconds = value; }

    /** Возвращает настройку maxRoundSeconds из введённой формы. */
    public int getMaxRoundSeconds() { return maxRoundSeconds; }
    /** Принимает настройку maxRoundSeconds; допустимость проверяет сервис до записи в БД. */
    public void setMaxRoundSeconds(int value) { maxRoundSeconds = value; }

    /** Возвращает настройку maxPlayers из введённой формы. */
    public int getMaxPlayers() { return maxPlayers; }
    /** Принимает настройку maxPlayers; допустимость проверяет сервис до записи в БД. */
    public void setMaxPlayers(int value) { maxPlayers = value; }

    /** Возвращает настройку questionCountEditable из введённой формы. */
    public boolean isQuestionCountEditable() { return questionCountEditable; }
    /** Принимает настройку questionCountEditable; допустимость проверяет сервис до записи в БД. */
    public void setQuestionCountEditable(boolean value) { questionCountEditable = value; }

    /** Возвращает настройку roundSecondsEditable из введённой формы. */
    public boolean isRoundSecondsEditable() { return roundSecondsEditable; }
    /** Принимает настройку roundSecondsEditable; допустимость проверяет сервис до записи в БД. */
    public void setRoundSecondsEditable(boolean value) { roundSecondsEditable = value; }

    /** Возвращает настройку gradeIds из введённой формы. */
    public List<Long> getGradeIds() { return gradeIds; }
    /** Принимает настройку gradeIds; допустимость проверяет сервис до записи в БД. */
    public void setGradeIds(List<Long> value) { gradeIds = value; }

    /** Возвращает настройку vocabularySetId из введённой формы. */
    public Long getVocabularySetId() { return vocabularySetId; }
    /** Принимает настройку vocabularySetId; допустимость проверяет сервис до записи в БД. */
    public void setVocabularySetId(Long value) { vocabularySetId = value; }

    /** Возвращает настройку sourceLanguage из введённой формы. */
    public String getSourceLanguage() { return sourceLanguage; }
    /** Принимает настройку sourceLanguage; допустимость проверяет сервис до записи в БД. */
    public void setSourceLanguage(String value) { sourceLanguage = value; }

    /** Возвращает настройку targetLanguage из введённой формы. */
    public String getTargetLanguage() { return targetLanguage; }
    /** Принимает настройку targetLanguage; допустимость проверяет сервис до записи в БД. */
    public void setTargetLanguage(String value) { targetLanguage = value; }

    /** Возвращает настройку subjectId из введённой формы. */
    public Long getSubjectId() { return subjectId; }
    /** Принимает настройку subjectId; допустимость проверяет сервис до записи в БД. */
    public void setSubjectId(Long value) { subjectId = value; }

    /** Возвращает настройку difficulty из введённой формы. */
    public Integer getDifficulty() { return difficulty; }
    /** Принимает настройку difficulty; допустимость проверяет сервис до записи в БД. */
    public void setDifficulty(Integer value) { difficulty = value; }

    /** Возвращает настройку questionTypes из введённой формы. */
    public Set<QuestionType> getQuestionTypes() { return questionTypes; }
    /** Принимает настройку questionTypes; допустимость проверяет сервис до записи в БД. */
    public void setQuestionTypes(Set<QuestionType> value) { questionTypes = value; }

    /** Возвращает настройку factSetId из введённой формы. */
    public Long getFactSetId() { return factSetId; }
    /** Принимает настройку factSetId; допустимость проверяет сервис до записи в БД. */
    public void setFactSetId(Long value) { factSetId = value; }
}
