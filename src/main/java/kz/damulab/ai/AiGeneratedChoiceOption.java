package kz.damulab.ai;

import com.fasterxml.jackson.annotation.JsonAlias;

/** Вариант SCQ/MCQ в ответе LLM; алиасы — для DeepSeek без strict schema. */
public record AiGeneratedChoiceOption(
        String label,
        @JsonAlias({"text_ru", "textRU"}) String textRu,
        @JsonAlias({"text_kk", "textKK", "textKz", "text_kz"}) String textKk,
        boolean correct
) {
}
