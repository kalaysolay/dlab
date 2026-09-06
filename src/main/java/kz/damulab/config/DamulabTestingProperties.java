package kz.damulab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "damulab.testing")
public class DamulabTestingProperties {

    /**
     * Target number of questions per session when the bank has enough items (capped by max and by pool size).
     */
    private int defaultQuestionCount = 12;

    /**
     * Hard upper bound for how many questions we attach to a session.
     */
    private int maxQuestionCount = 20;

    /**
     * Minimum published questions required for a subject+grade pair to appear in student pickers.
     */
    private int minPublishedPerSubjectGrade = 1;

    /**
     * Стратегия подбора для учебного предметного теста. ADAPTIVE использует mastery и историю
     * ученика; RANDOM оставлен как безопасный переключатель для отката поведения.
     */
    private QuestionSelectionStrategy selectionStrategy = QuestionSelectionStrategy.ADAPTIVE;

    /** Количество последних сессий, вопросы из которых получают штраф за повтор. */
    private int recentSessionWindow = 3;

    /** Мягкий максимум доли одной темы в тесте; ослабляется, если иначе тест не заполнить. */
    private int maxTopicSharePercent = 40;

    /** Через сколько дней освоенный навык снова получает повышенный приоритет для проверки. */
    private int masteryStaleAfterDays = 30;

    public int getDefaultQuestionCount() {
        return defaultQuestionCount;
    }

    public void setDefaultQuestionCount(int defaultQuestionCount) {
        this.defaultQuestionCount = defaultQuestionCount;
    }

    public int getMaxQuestionCount() {
        return maxQuestionCount;
    }

    public void setMaxQuestionCount(int maxQuestionCount) {
        this.maxQuestionCount = maxQuestionCount;
    }

    public int getMinPublishedPerSubjectGrade() {
        return minPublishedPerSubjectGrade;
    }

    public void setMinPublishedPerSubjectGrade(int minPublishedPerSubjectGrade) {
        this.minPublishedPerSubjectGrade = minPublishedPerSubjectGrade;
    }

    public QuestionSelectionStrategy getSelectionStrategy() {
        return selectionStrategy;
    }

    public void setSelectionStrategy(QuestionSelectionStrategy selectionStrategy) {
        this.selectionStrategy = selectionStrategy == null ? QuestionSelectionStrategy.ADAPTIVE : selectionStrategy;
    }

    public int getRecentSessionWindow() {
        return recentSessionWindow;
    }

    public void setRecentSessionWindow(int recentSessionWindow) {
        this.recentSessionWindow = recentSessionWindow;
    }

    public int getMaxTopicSharePercent() {
        return maxTopicSharePercent;
    }

    public void setMaxTopicSharePercent(int maxTopicSharePercent) {
        this.maxTopicSharePercent = maxTopicSharePercent;
    }

    public int getMasteryStaleAfterDays() {
        return masteryStaleAfterDays;
    }

    public void setMasteryStaleAfterDays(int masteryStaleAfterDays) {
        this.masteryStaleAfterDays = masteryStaleAfterDays;
    }
}
