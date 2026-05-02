package kz.damulab.testing;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

public record SubmitAnswerRequest(
        @NotNull Long sessionQuestionId,
        @NotNull JsonNode answer
) {
}
