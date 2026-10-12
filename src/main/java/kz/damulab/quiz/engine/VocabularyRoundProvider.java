package kz.damulab.quiz.engine;

import java.util.*;
import kz.damulab.quiz.*;
import kz.damulab.quiz.catalog.*;
import org.springframework.stereotype.Component;

/** Словарный провайдер использует общую policy дистракторов и фиксирует четыре варианта один раз на комнату. */
@Component
public class VocabularyRoundProvider implements QuizRoundProvider {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(VocabularyRoundProvider.class);
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
        var config = config(room);
        var set = config.getVocabularySet(); var published = set.getPublishedVersion();
        var data = distractors.prepareForGame(set); List<Source> result = new ArrayList<>();
        var pool = distractors.forAudience(data, required);
        int rejectedDistractors=0;
        for (var v : pool.sources(set.getId())) {
            var answers = data.answers().getOrDefault(v.getId(), List.of());
            if (!pool.playable(v)) { rejectedDistractors++; continue; }
            String primary = answers.stream().filter(VocabularyItemAnswer::isPrimary).findFirst().orElseThrow().getAnswerText();
            result.add(new Source(v.getId(), (order, random) -> {
                List<String> texts = new ArrayList<>(); texts.add(primary);
                // Предпочтения части речи/сложности/набора определяет общий сервис, а показанный порядок перемешивается.
                var candidates=new ArrayList<>(pool.candidates(v));
                // Стабильная сортировка после shuffle сохраняет общие предпочтения и случайный выбор среди равных.
                // Иначе в большом банке почти каждый раунд предлагал первые три слова по ID.
                Collections.shuffle(candidates,random);
                candidates.sort(VocabularyDistractorService.CANDIDATE_PREFERENCE);
                for (int i = 0; i < 3; i++) texts.add(candidates.get(i).answerText());
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
        // Один итог на проверку банка: слова, переводы и правильные варианты не журналируются.
        if(rejectedDistractors>0) log.warn("quiz_distractors_rejected room={} count={}",room.getCode(),rejectedDistractors);
        return List.copyOf(result);
    }
    /** Проверка лобби не сортирует варианты всех слов и прекращается при достаточном количестве. */
    public int countUpTo(QuizRoom room, Set<Long> required, int limit) {
        var set=config(room).getVocabularySet();
        var pool=distractors.forAudience(distractors.prepareForGame(set),required);
        return (int)pool.sources(set.getId()).stream().filter(pool::playable).limit(limit).count();
    }
    private QuizWordGameConfig config(QuizRoom room) {
        var config=configs.findById(room.getGameVersion().getId()).orElseThrow(()->new QuizEngineException("SOURCE_UNAVAILABLE"));
        var set=config.getVocabularySet();
        if(!QuizContentEligibility.wordSetComplete(set) || !Objects.equals(set.getSourceLanguage(),config.getSourceLanguage())
                || !Objects.equals(set.getTargetLanguage(),config.getTargetLanguage())) throw new QuizEngineException("SOURCE_UNAVAILABLE");
        return config;
    }
}
