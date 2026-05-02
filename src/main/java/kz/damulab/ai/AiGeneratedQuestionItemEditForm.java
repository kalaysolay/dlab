package kz.damulab.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AiGeneratedQuestionItemEditForm {

    @NotBlank
    private String bodyRu;

    @NotBlank
    private String bodyKk;

    private String explanationRu;
    private String explanationKk;

    @NotBlank
    @Size(max = 512)
    private String source;

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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
