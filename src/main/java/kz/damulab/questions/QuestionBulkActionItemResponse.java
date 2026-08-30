package kz.damulab.questions;

/** Результат одной строки пакетной операции: обновлённый вопрос либо код бизнес-ошибки. */
public record QuestionBulkActionItemResponse(
        Long questionId,
        QuestionResponse question,
        String error
) {
}
