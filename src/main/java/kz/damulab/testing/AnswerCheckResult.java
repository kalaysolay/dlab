package kz.damulab.testing;

import java.math.BigDecimal;

public record AnswerCheckResult(boolean correct, BigDecimal pointsAwarded, String detailsJson) {
}
