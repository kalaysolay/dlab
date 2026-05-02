package kz.damulab.questions;

public class QuestionFlagRequest {

    private QuestionFlagSource source = QuestionFlagSource.METHODIST;
    private String reason;

    public QuestionFlagSource getSource() {
        return source;
    }

    public void setSource(QuestionFlagSource source) {
        this.source = source == null ? QuestionFlagSource.METHODIST : source;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
