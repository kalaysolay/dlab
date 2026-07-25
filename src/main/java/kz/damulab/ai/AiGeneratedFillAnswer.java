package kz.damulab.ai;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonAlias;

import kz.damulab.questions.FillMatchMode;

/** Ответ FILL_IN в ответе LLM; алиасы — для DeepSeek без strict schema. */
public record AiGeneratedFillAnswer(
        String placeholder,
        String answer,
        @JsonAlias({"match_mode"}) FillMatchMode matchMode,
        BigDecimal tolerance
) {
}
