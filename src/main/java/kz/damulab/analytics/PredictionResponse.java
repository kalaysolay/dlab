package kz.damulab.analytics;

public record PredictionResponse(
        int expectedPercent,
        String confidence,
        int basedOnResults,
        String message
) {
}
