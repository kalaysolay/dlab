package kz.damulab.content;

public class ContentGraphException extends RuntimeException {

    private final String code;

    public ContentGraphException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
