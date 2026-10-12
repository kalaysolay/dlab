package kz.damulab.quiz.engine;

/** Ошибка движка со стабильным кодом; QuizExceptionHandler переводит его в безопасный локализованный HTTP-отказ. */
public class QuizEngineException extends RuntimeException {
    private final String code;
    private final Integer available;
    private final Integer required;
    /** Создаёт отказ без раскрытия учебного ключа. */
    public QuizEngineException(String code) { this(code, null, null); }
    /** Создаёт отказ с числом пригодных и необходимых источников. */
    public QuizEngineException(String code, Integer available, Integer required) {
        super(code); this.code = code; this.available = available; this.required = required;
    }
    /** Возвращает код для будущего API. */
    public String getCode() { return code; }
    /** Возвращает доступное количество при нехватке. */
    public Integer getAvailable() { return available; }
    /** Возвращает запрошенное количество при нехватке. */
    public Integer getRequired() { return required; }
}
