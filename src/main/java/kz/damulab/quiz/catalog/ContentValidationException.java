package kz.damulab.quiz.catalog;

import java.util.List;

/** Ошибка бизнес-валидации редакционного материала с безопасными сообщениями для формы. */
public class ContentValidationException extends RuntimeException {
    private final List<String> errors;

    public ContentValidationException(String error) { this(List.of(error)); }
    public ContentValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    /** Возвращает все причины, чтобы администратор исправил их за один проход. */
    public List<String> getErrors() { return errors; }
}
