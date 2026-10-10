package kz.damulab.quiz.engine;

import java.util.*;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.*;
import org.springframework.stereotype.Component;

/** Словарный провайдер использует общую policy дистракторов и фиксирует четыре варианта один раз на комнату. */
@Component
public class VocabularyRoundProvider implements QuizRoundProvider {
    private final QuizWordGameConfigRepository configs;
    private final VocabularyDistractorService distractors;
    private final QuizSnapshotJson json;
    /** Подключает зависимости движка; создание компонента не читает и не изменяет БД. */
    public VocabularyRoundProvider(QuizWordGameConfigRepository configs, VocabularyDistractorService distractors, QuizSnapshotJson json) {
        this.configs = configs; this.distractors = distractors; this.json = json;
    }
    /** Обслуживает перевод слов. */
    public QuizGameType gameType() { return QuizGameType.WORD_TRANSLATION; }
    /** Получает публикации/переводы/запреты пачкой и исключает слова без трёх безопасных дистракторов. */
    public List<Source> prepare(QuizRoom room, Set<Long> required) {
        var config = configs.findById(room.getGameVersion().getId()).orElseThrow(() -> new QuizEngineException("SOURCE_UNAVAILABLE"));
        var set = config.getVocabularySet(); var published = set.getPublishedVersion();
        if (!QuizContentEligibility.wordSetComplete(set)
                || !Objects.equals(set.getSourceLanguage(), config.getSourceLanguage()) || !Objects.equals(set.getTargetLanguage(), config.getTargetLanguage()))
            throw new QuizEngineException("SOURCE_UNAVAILABLE");
        var data = distractors.prepareForGame(set); List<Source> result = new ArrayList<>();
        for (var v : data.versions()) {
            if (!v.getItem().getSet().getId().equals(set.getId()) || !QuizContentEligibility.audience(v.isUniversalAudience(), v.getGradeIds(), required)) continue;
            var answers = data.answers().getOrDefault(v.getId(), List.of());
            if (!QuizContentEligibility.wordComplete(v, answers)) continue;
            var diagnosis = distractors.diagnose(v, required, data);
            if (!diagnosis.playable()) continue;
            String primary = answers.stream().filter(VocabularyItemAnswer::isPrimary).findFirst().orElseThrow().getAnswerText();
            result.add(new Source(v.getId(), (order, random) -> {
                List<String> texts = new ArrayList<>(); texts.add(primary);
                // Предпочтения части речи/сложности/набора определяет общий сервис, а показанный порядок перемешивается.
                for (int i = 0; i < 3; i++) texts.add(diagnosis.candidates().get(i).answerText());
                Collections.shuffle(texts, random);
                List<Map<String, String>> options = new ArrayList<>(); String correctId = null;
                for (int i = 0; i < texts.size(); i++) {
                    String id = String.valueOf((char) ('A' + i));
                    options.add(Map.of("id", id, "text", texts.get(i)));
                    if (texts.get(i).equals(primary)) correctId = id;
                }
                QuizRound round = QuizRound.vocabulary(room, v, published, order);
                round.snapshot(v.getSourceText(), json.write(options), json.write(correctId),
                        QuizSnapshotJson.localized(v.getMeaningNoteRu(), v.getMeaningNoteKk(), room.getLanguage()), v.getSourceReference(),
                        json.write(Map.of("format", "TRANSLATION", "sourceVersionId", v.getId(), "setVersionId", published.getId(),
                                "sourceLanguage", config.getSourceLanguage(), "targetLanguage", config.getTargetLanguage(),
                                "pronunciation", Objects.toString(v.getPronunciation(), ""))), room.getLanguage());
                return round;
            }));
        }
        return List.copyOf(result);
    }
}
