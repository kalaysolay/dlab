package kz.damulab.questions;

import java.math.BigDecimal;

public class FillAnswerForm {

    private String placeholder;
    private String answer;
    private FillMatchMode matchMode = FillMatchMode.EXACT;
    private BigDecimal tolerance;

    public FillAnswerForm() {
    }

    public FillAnswerForm(String placeholder, String answer, FillMatchMode matchMode, BigDecimal tolerance) {
        this.placeholder = placeholder;
        this.answer = answer;
        this.matchMode = matchMode;
        this.tolerance = tolerance;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public FillMatchMode getMatchMode() {
        return matchMode;
    }

    public void setMatchMode(FillMatchMode matchMode) {
        this.matchMode = matchMode;
    }

    public BigDecimal getTolerance() {
        return tolerance;
    }

    public void setTolerance(BigDecimal tolerance) {
        this.tolerance = tolerance;
    }
}
