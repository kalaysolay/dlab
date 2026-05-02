package kz.damulab.ai;

public class AiContentFactoryException extends RuntimeException {

    private final String code;

    public AiContentFactoryException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
