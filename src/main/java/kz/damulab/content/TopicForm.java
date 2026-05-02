package kz.damulab.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TopicForm {

    @NotNull
    private Long subjectId;

    @NotNull
    private Long gradeId;

    private Long parentId;

    @Size(max = 128)
    private String code;

    @NotBlank
    @Size(max = 255)
    private String titleRu;

    @NotBlank
    @Size(max = 255)
    private String titleKk;

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

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
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
}
