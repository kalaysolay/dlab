package kz.damulab.speaking;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.*;

/** API-контракты без JPA-сущностей, внутренних идентификаторов ученика и произвольного HTML. */
public final class SpeakingViews {
    private SpeakingViews() {}
    public record CreateRequest(@NotBlank @Size(max=64) String scenarioCode,
            @NotNull @Pattern(regexp="A2|B1|B2") String level,
            @NotNull @Pattern(regexp="ru|kk") String explanationLanguage) {}
    public record AnswerRequest(@NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Size(max=2000) String text) {}
    public record VersionRequest(@NotNull @PositiveOrZero Long expectedVersion) {}
    public record Observation(UUID id, UUID turnId, String skillCode, String original, String corrected,
            String explanation, String kind, String evidence, String source, boolean assisted) {}
    public record Turn(UUID id, int sequence, String source, String text, String reactionText,
            String questionText, OffsetDateTime createdAt) {}
    public record Session(UUID id, String status, long version, SpeakingScenario scenario, String level,
            String explanationLanguage, String openingText, String firstQuestion, int acceptedTurns,
            String providerMode, List<Turn> turns, List<Observation> observations) {}
    public record HistoryItem(UUID id, String title, String status, int acceptedTurns, OffsetDateTime createdAt) {}
    public record Difficulty(String skillCode, long observations, long sessions, OffsetDateTime lastObserved,
            String status) {}
}
