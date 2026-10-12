package kz.damulab.quiz;

// Контракт предыдущего бинарника нужен только для операторской проверки отката, не для приложения.

public class QuizException extends RuntimeException {

    public QuizException(String message) {
        super(message);
    }
}
