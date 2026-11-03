package kz.damulab.speaking;

import java.util.List;
import java.util.Map;

/** Версионируемая учебная карточка. Копия целиком закрепляется в созданной сессии. */
public record SpeakingScenario(String code, int version, String titleRu, String titleKk,
        String englishTitle, String descriptionRu, String descriptionKk, String icon,
        String opening, Map<String, String> firstQuestions, List<String> questions, List<String> hints) {
    public String title(String language) { return "kk".equals(language) ? titleKk : titleRu; }
}
