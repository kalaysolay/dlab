package kz.damulab.testing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class StartTestSessionRequest {

    @NotNull
    private TestType testType = TestType.SUBJECT;

    @NotNull
    private Long subjectId;

    @NotNull
    private Long gradeId;

    /** null означает тест по всем темам выбранных предмета и класса. */
    @Min(1)
    private Long topicId;

    @Pattern(regexp = "ru|kk")
    private String language = "ru";

    @Min(1)
    @Max(5)
    private Integer difficulty;

    /**
     * Ignored for student sessions: size is taken from server configuration (see {@code damulab.testing}).
     * Optional for API compatibility.
     */
    @Min(1)
    @Max(20)
    private Integer questionCount;

    public TestType getTestType() {
        return testType;
    }

    public void setTestType(TestType testType) {
        this.testType = testType;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public Long getGradeId() {
        return gradeId;
    }

    public void setGradeId(Long gradeId) {
        this.gradeId = gradeId;
    }

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public Integer getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }
}
