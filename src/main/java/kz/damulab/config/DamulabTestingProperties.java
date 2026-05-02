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
}
