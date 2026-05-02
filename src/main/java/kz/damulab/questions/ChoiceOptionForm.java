package kz.damulab.questions;

import com.fasterxml.jackson.annotation.JsonAlias;

public class ChoiceOptionForm {

    private String label;
    private String textRu;
    private String textKk;
    private boolean correct;
    @JsonAlias("soft_delete")
    private boolean softDeleted;

    public ChoiceOptionForm() {
    }

    public ChoiceOptionForm(String label, String textRu, String textKk, boolean correct) {
        this.label = label;
        this.textRu = textRu;
        this.textKk = textKk;
        this.correct = correct;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getTextRu() {
        return textRu;
    }

    public void setTextRu(String textRu) {
        this.textRu = textRu;
    }

    public String getTextKk() {
        return textKk;
    }

    public void setTextKk(String textKk) {
        this.textKk = textKk;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    public boolean isSoftDeleted() {
        return softDeleted;
    }

    public void setSoftDeleted(boolean softDeleted) {
        this.softDeleted = softDeleted;
    }
}
