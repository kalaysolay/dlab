package kz.damulab.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AtomicSkillForm {

    @NotNull
    private Long topicId;

    @Size(max = 128)
    private String code;

    @NotBlank
    @Size(max = 255)
    private String titleRu;

    @NotBlank
    @Size(max = 255)
    private String titleKk;

    private boolean active = true;

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
