package kz.damulab.quiz;

// Контракт предыдущего бинарника нужен только для операторской проверки отката, не для приложения.

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

public record SubmitQuizAnswerRequest(
        @NotNull Long roundId,
        @NotNull JsonNode answer
) {
}
