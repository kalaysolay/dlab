package kz.damulab.content;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class TopicImportRequest {

    private Long subjectId;

    @Size(max = 64)
    private String subjectCode;

    private Long gradeId;

    private Integer gradeNo;

    @Size(max = 512)
    private String importNote;

    @Valid
    @NotEmpty
    private List<TopicImportItem> topics = List.of();

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }

    public Long getGradeId() {
        return gradeId;
    }

    public void setGradeId(Long gradeId) {
        this.gradeId = gradeId;
    }

    public Integer getGradeNo() {
        return gradeNo;
    }

    public void setGradeNo(Integer gradeNo) {
        this.gradeNo = gradeNo;
    }

    public String getImportNote() {
        return importNote;
    }

    public void setImportNote(String importNote) {
        this.importNote = importNote;
    }

    public List<TopicImportItem> getTopics() {
        return topics == null ? List.of() : topics;
    }

    public void setTopics(List<TopicImportItem> topics) {
        this.topics = topics;
    }
}
