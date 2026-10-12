package kz.damulab.quiz.engine;

import java.util.*;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.*;
import org.springframework.stereotype.Component;

/** Снимки фактов из текущих проверенных публикаций; false сохраняется как boolean, источник закрыт до разбора. */
@Component
public class FactRoundProvider implements QuizRoundProvider {
    private final QuizFactGameConfigRepository configs;
    private final FactStatementVersionRepository versions;
    private final QuizSnapshotJson json;
    /** Подключает зависимости движка; создание компонента не читает и не изменяет БД. */
    public FactRoundProvider(QuizFactGameConfigRepository configs, FactStatementVersionRepository versions, QuizSnapshotJson json) {
        this.configs = configs; this.versions = versions; this.json = json;
    }
    /** Обслуживает утверждения. */
    public QuizGameType gameType() { return QuizGameType.TRUE_FALSE; }
    /** Повторно проверяет опубликованный набор и фактическую аудиторию, без доверия административному preview. */
    public List<Source> prepare(QuizRoom room, Set<Long> required) {
        FactSet set = configs.findById(room.getGameVersion().getId()).orElseThrow(() -> new QuizEngineException("SOURCE_UNAVAILABLE")).getFactSet();
        if (set.getStatus() != EditorialStatus.PUBLISHED || QuizContentEligibility.blank(set.getSourcePolicy())) throw new QuizEngineException("SOURCE_UNAVAILABLE");
        return versions.findPublishedCurrentVersionsInSet(set.getId()).stream()
                .filter(v -> v.getStatement().getSet().getId().equals(set.getId()))
                .filter(v -> QuizContentEligibility.factComplete(v) && QuizContentEligibility.audience(v.isUniversalAudience(), v.getGradeIds(), required))
                .map(v -> new Source(v.getId(), (order, random) -> snapshot(room, v, order))).toList();
    }
    private QuizRound snapshot(QuizRoom room, FactStatementVersion v, int order) {
        QuizRound round = QuizRound.fact(room, v, order);
        round.snapshot(QuizSnapshotJson.localized(v.getTextRu(), v.getTextKk(), room.getLanguage()), "[true,false]",
                json.write(v.isTruthValue()), QuizSnapshotJson.localized(v.getExplanationRu(), v.getExplanationKk(), room.getLanguage()),
                v.getSourceReference(), json.write(Map.of("format", "BOOLEAN", "sourceVersionId", v.getId(), "setId", v.getStatement().getSet().getId())), room.getLanguage());
        return round;
    }
}
