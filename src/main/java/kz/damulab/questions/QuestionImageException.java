package kz.damulab.questions;

/** Ошибка проверки или файлового хранения изображения из редактора вопроса. */
public class QuestionImageException extends RuntimeException {

    private final String code;

    public QuestionImageException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
