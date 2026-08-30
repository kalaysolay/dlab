package kz.damulab.questions;

import java.util.List;

/** Сводка пакетной операции с независимым результатом для каждого выбранного вопроса. */
public record QuestionBulkActionResponse(
        String action,
        int succeeded,
        int failed,
        List<QuestionBulkActionItemResponse> items
) {
}
