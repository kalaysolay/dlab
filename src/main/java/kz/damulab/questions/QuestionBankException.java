package kz.damulab.questions;

public class QuestionBankException extends RuntimeException {

    private final String code;

    public QuestionBankException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
