package kz.damulab.ai;

import java.math.BigDecimal;

import kz.damulab.questions.FillMatchMode;

public record AiGeneratedFillAnswer(
        String placeholder,
        String answer,
        FillMatchMode matchMode,
        BigDecimal tolerance
) {
}
