package kz.damulab.testing;

public record MatchingResultRow(
        String leftText,
        String studentRightText,
        String correctRightText,
        boolean rowCorrect
) {
}
