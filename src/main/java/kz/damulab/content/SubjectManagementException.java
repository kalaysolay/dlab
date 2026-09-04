package kz.damulab.content;

/** Ошибка административных операций с предметом. */
public class SubjectManagementException extends RuntimeException {

    private final String code;

    /** Создаёт ошибку с кодом, который контроллер преобразует в понятный текст. */
    public SubjectManagementException(String code) {
        super(code);
        this.code = code;
    }

    /** Возвращает стабильный код ошибки. */
    /** Возвращает стабильный код ошибки. */
    public String getCode() {
        return code;
    }
}
