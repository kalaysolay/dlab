package kz.damulab.quiz;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

public record SubmitQuizAnswerRequest(
        @NotNull Long roundId,
        @NotNull JsonNode answer
) {
}
