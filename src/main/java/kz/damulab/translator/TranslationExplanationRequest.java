package kz.damulab.translator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Пара текстов для учебного разбора; сервер не хранит её между запросами. */
public record TranslationExplanationRequest(
        @NotNull TranslationDirection direction,
        @NotBlank @Size(max = 5000) String sourceText,
        @NotBlank @Size(max = 10000) String translatedText,
        boolean economyMode
) {
}
