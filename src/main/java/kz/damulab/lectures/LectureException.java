package kz.damulab.lectures;

public class LectureException extends RuntimeException {

    private final String code;

    public LectureException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
