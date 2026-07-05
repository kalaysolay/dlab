package kz.damulab.content;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TopicImportItem {

    @Size(max = 128)
    private String code;

    @NotBlank
    private String titleRu;

    @NotBlank
    private String titleKk;

    @Valid
    private List<TopicImportItem> children = List.of();

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

    public List<TopicImportItem> getChildren() {
        return children == null ? List.of() : children;
    }

    public void setChildren(List<TopicImportItem> children) {
        this.children = children;
    }
}
