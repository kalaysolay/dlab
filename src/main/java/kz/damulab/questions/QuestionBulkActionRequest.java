package kz.damulab.questions;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Запрос пакетного изменения статуса; лимит защищает endpoint от случайно огромной операции. */
public record QuestionBulkActionRequest(
        @NotEmpty @Size(max = 100) List<@NotNull Long> questionIds,
        @NotNull QuestionBulkAction action
) {
}
