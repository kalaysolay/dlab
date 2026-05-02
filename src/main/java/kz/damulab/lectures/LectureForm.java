package kz.damulab.lectures;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class LectureForm {

    private Long topicId;
    private String titleRu;
    private String titleKk;
    private String contentRu;
    private String contentKk;
    private String source;
    private LectureControlMode controlMode = LectureControlMode.NONE;

    @Min(0)
    @Max(10)
    private int autoCheckpointCount = 0;

    private List<Long> checkpointQuestionVersionIds = new ArrayList<>();
    private List<LectureAttachmentForm> attachments = new ArrayList<>();

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    public String getTitleRu() {
        return titleRu;
    }

    public void setTitleRu(String titleRu) {
        this.titleRu = titleRu;
    }

    public String getTitleKk() {
        return titleKk;
    }

    public void setTitleKk(String titleKk) {
        this.titleKk = titleKk;
    }

    public String getContentRu() {
        return contentRu;
    }

    public void setContentRu(String contentRu) {
        this.contentRu = contentRu;
    }

    public String getContentKk() {
        return contentKk;
    }

    public void setContentKk(String contentKk) {
        this.contentKk = contentKk;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LectureControlMode getControlMode() {
        return controlMode;
    }

    public void setControlMode(LectureControlMode controlMode) {
        this.controlMode = controlMode == null ? LectureControlMode.NONE : controlMode;
    }

    public int getAutoCheckpointCount() {
        return autoCheckpointCount;
    }

    public void setAutoCheckpointCount(int autoCheckpointCount) {
        this.autoCheckpointCount = autoCheckpointCount;
    }

    public List<Long> getCheckpointQuestionVersionIds() {
        return checkpointQuestionVersionIds;
    }

    public void setCheckpointQuestionVersionIds(List<Long> checkpointQuestionVersionIds) {
        this.checkpointQuestionVersionIds = checkpointQuestionVersionIds == null
                ? new ArrayList<>()
                : new ArrayList<>(checkpointQuestionVersionIds);
    }

    public List<LectureAttachmentForm> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<LectureAttachmentForm> attachments) {
        this.attachments = attachments == null ? new ArrayList<>() : new ArrayList<>(attachments);
    }
}
