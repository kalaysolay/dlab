package kz.damulab.translator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Проверенный JSON-контракт запроса на перевод. */
public record TranslationRequest(
        @NotNull TranslationDirection direction,
        @NotBlank @Size(max = 5000) String text
) {
}
