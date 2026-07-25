package kz.damulab.ai;

import com.fasterxml.jackson.annotation.JsonAlias;

/** Пара MATCHING в ответе LLM; алиасы — для DeepSeek без strict schema. */
public record AiGeneratedMatchingPair(
        @JsonAlias({"left_ru", "leftRU"}) String leftRu,
        @JsonAlias({"left_kk", "leftKK", "leftKz", "left_kz"}) String leftKk,
        @JsonAlias({"right_ru", "rightRU"}) String rightRu,
        @JsonAlias({"right_kk", "rightKK", "rightKz", "right_kz"}) String rightKk
) {
}
