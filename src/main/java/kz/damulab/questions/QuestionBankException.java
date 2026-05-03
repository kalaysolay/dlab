package kz.damulab.questions;

import java.util.Collections;
import java.util.List;

public class QuestionBankException extends RuntimeException {

    private final String code;
    private final List<Integer> invalidChoiceOptionIndexes;

    public QuestionBankException(String code) {
        this(code, Collections.emptyList());
    }

    public QuestionBankException(String code, List<Integer> invalidChoiceOptionIndexes) {
        super(code);
        this.code = code;
        this.invalidChoiceOptionIndexes = invalidChoiceOptionIndexes == null
                ? Collections.emptyList()
                : List.copyOf(invalidChoiceOptionIndexes);
    }

    public String getCode() {
        return code;
    }

    /**
     * Индексы вариантов в {@link QuestionForm#getOptions()} с неполным RU/KK (для подсветки в форме).
     */
    public List<Integer> getInvalidChoiceOptionIndexes() {
        return invalidChoiceOptionIndexes;
    }
}
