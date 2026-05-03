package kz.damulab.questions;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class QuestionForm {

    @NotNull
    private Long subjectId;

    @NotEmpty
    private List<Long> topicIds = new ArrayList<>();

    @NotEmpty
    private List<Long> gradeIds = new ArrayList<>();

    private Long atomicSkillId;

    @NotNull
    private QuestionType type = QuestionType.SCQ;

    @Min(1)
    @Max(5)
    private int difficulty = 2;

    @NotBlank
    private String bodyRu;

    @NotBlank
    private String bodyKk;

    @NotBlank
    private String source;

    private String explanationRu;
    private String explanationKk;
    private String miniLectureRu;
    private String miniLectureKk;
    private QuestionStatus status = QuestionStatus.DRAFT;
    private List<ChoiceOptionForm> options = new ArrayList<>();
    private List<MatchingPairForm> matchingPairs = new ArrayList<>();
    private List<FillAnswerForm> fillAnswers = new ArrayList<>();

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public List<Long> getTopicIds() {
        return topicIds;
    }

    public void setTopicIds(List<Long> topicIds) {
        this.topicIds = topicIds == null ? new ArrayList<>() : topicIds;
    }

    public List<Long> getGradeIds() {
        return gradeIds;
    }

    public void setGradeIds(List<Long> gradeIds) {
        this.gradeIds = gradeIds == null ? new ArrayList<>() : gradeIds;
    }

    public Long getAtomicSkillId() {
        return atomicSkillId;
    }

    public void setAtomicSkillId(Long atomicSkillId) {
        this.atomicSkillId = atomicSkillId;
    }

    public QuestionType getType() {
        return type;
    }

    public void setType(QuestionType type) {
        this.type = type;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public String getBodyRu() {
        return bodyRu;
    }

    public void setBodyRu(String bodyRu) {
        this.bodyRu = bodyRu;
    }

    public String getBodyKk() {
        return bodyKk;
    }

    public void setBodyKk(String bodyKk) {
        this.bodyKk = bodyKk;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getExplanationRu() {
        return explanationRu;
    }

    public void setExplanationRu(String explanationRu) {
        this.explanationRu = explanationRu;
    }

    public String getExplanationKk() {
        return explanationKk;
    }

    public void setExplanationKk(String explanationKk) {
        this.explanationKk = explanationKk;
    }

    public String getMiniLectureRu() {
        return miniLectureRu;
    }

    public void setMiniLectureRu(String miniLectureRu) {
        this.miniLectureRu = miniLectureRu;
    }

    public String getMiniLectureKk() {
        return miniLectureKk;
    }

    public void setMiniLectureKk(String miniLectureKk) {
        this.miniLectureKk = miniLectureKk;
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public void setStatus(QuestionStatus status) {
        this.status = status;
    }

    public List<ChoiceOptionForm> getOptions() {
        return options;
    }

    public void setOptions(List<ChoiceOptionForm> options) {
        this.options = options == null ? new ArrayList<>() : options;
    }

    public List<MatchingPairForm> getMatchingPairs() {
        return matchingPairs;
    }

    public void setMatchingPairs(List<MatchingPairForm> matchingPairs) {
        this.matchingPairs = matchingPairs == null ? new ArrayList<>() : matchingPairs;
    }

    public List<FillAnswerForm> getFillAnswers() {
        return fillAnswers;
    }

    public void setFillAnswers(List<FillAnswerForm> fillAnswers) {
        this.fillAnswers = fillAnswers == null ? new ArrayList<>() : fillAnswers;
    }
}
