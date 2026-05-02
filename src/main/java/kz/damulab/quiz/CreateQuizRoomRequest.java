package kz.damulab.quiz;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateQuizRoomRequest {

    @NotNull
    private Long subjectId;

    @NotNull
    private Long gradeId;

    @Size(max = 8)
    private String language = "ru";

    @Min(1)
    @Max(5)
    private Integer difficulty;

    @Min(1)
    @Max(12)
    private Integer questionCount = 5;

    @Min(5)
    @Max(120)
    private Integer roundSeconds = 20;

    @Min(2)
    @Max(4)
    private Integer maxPlayers = 4;

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

    public Integer getRoundSeconds() {
        return roundSeconds;
    }

    public void setRoundSeconds(Integer roundSeconds) {
        this.roundSeconds = roundSeconds;
    }

    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }
}
