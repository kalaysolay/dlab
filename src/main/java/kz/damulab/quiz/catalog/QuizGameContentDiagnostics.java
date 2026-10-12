package kz.damulab.quiz.catalog;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import kz.damulab.content.*;
import kz.damulab.questions.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Пакетная диагностика трёх банков. Не создаёт раунды, не копирует вопросы и не раскрывает ключи. */
@Service
public class QuizGameContentDiagnostics {
    private final VocabularyDistractorService distractors;
    private final VocabularySetRepository words;
    private final FactSetRepository factSets;
    private final FactStatementVersionRepository facts;
    private final QuestionVersionRepository questions;
    private final SubjectRepository subjects;
    private final GradeRepository grades;

    public QuizGameContentDiagnostics(VocabularyDistractorService distractors, VocabularySetRepository words,
            FactSetRepository factSets, FactStatementVersionRepository facts, QuestionVersionRepository questions,
            SubjectRepository subjects, GradeRepository grades) {
        this.distractors = distractors; this.words = words; this.factSets = factSets; this.facts = facts;
        this.questions = questions; this.subjects = subjects; this.grades = grades;
    }

    /** Загружает источники и аудитории фиксированным числом запросов, независимо от количества игр. */
    @Transactional(readOnly = true)
    public Batch prepare() {
        return new Batch(distractors.prepare(List.of()), index(words.findForGameDiagnostics(), VocabularySet::getId),
                index(factSets.findAll(), FactSet::getId), facts.findPublishedCurrentVersions(),
                questions.findPublishedForQuizDiagnostics(), index(subjects.findAll(), Subject::getId),
                index(grades.findAllByOrderByGradeNoAsc(), Grade::getId));
    }

    /** Проверяет один набор требуемых классов общей policy; будущий start передаст фактические классы комнаты. */
    public Coverage coverage(QuizGameForm f, Set<Long> required, Batch data) {
        int published = 0, audience = 0, playable = 0, invalid = 0;
        List<Excluded> excluded = new ArrayList<>();
        if (f.getGameType() == QuizGameType.WORD_TRANSLATION) {
            for (VocabularyItemVersion v : data.words().versions()) {
                if (!v.getItem().getSet().getId().equals(f.getVocabularySetId())) continue;
                published++;
                String link = "/admin/vocabulary/items/" + v.getItem().getId() + "/edit";
                if (!QuizContentEligibility.audience(v.isUniversalAudience(), v.getGradeIds(), required)) { excluded.add(new Excluded(v.getId(), "Аудитория: не все требуемые классы", link)); continue; }
                audience++;
                if (!QuizContentEligibility.wordComplete(v, data.words().answers().getOrDefault(v.getId(), List.of()))) { invalid++; excluded.add(new Excluded(v.getId(), "Неподтверждённая или неполная публикация слова", link)); continue; }
                var result = distractors.diagnose(v, required, data.words());
                if (!result.playable()) { excluded.add(new Excluded(v.getId(), "Дистракторов " + result.candidates().size() + " из 3; " + result.excludedByReason(), link)); continue; }
                playable++;
            }
        } else if (f.getGameType() == QuizGameType.TRUE_FALSE) {
            for (FactStatementVersion v : data.facts()) {
                if (!v.getStatement().getSet().getId().equals(f.getFactSetId())) continue;
                published++;
                String link = "/admin/facts/statements/" + v.getStatement().getId() + "/edit";
                if (!QuizContentEligibility.audience(v.isUniversalAudience(), v.getGradeIds(), required)) { excluded.add(new Excluded(v.getId(), "Аудитория: не все требуемые классы", link)); continue; }
                audience++;
                if (!QuizContentEligibility.factComplete(v)) { invalid++; excluded.add(new Excluded(v.getId(), "Нет проверки, текста, объяснения или источника RU/KK", link)); continue; }
                playable++;
            }
        } else if (f.getGameType() == QuizGameType.SUBJECT_QUIZ) {
            for (QuestionVersion v : data.questions()) {
                if (!v.getSubject().getId().equals(f.getSubjectId())) continue;
                published++;
                String link = "/admin/questions/" + v.getQuestion().getId() + "/edit";
                // В старом банке нет явного universal: пустые gradeLinks не дают права показывать материал всем.
                if (!QuizContentEligibility.subjectAudience(v, required)) { excluded.add(new Excluded(v.getId(), "Аудитория предметного вопроса не подтверждена", link)); continue; }
                audience++;
                if (f.getQuestionTypes() == null || !f.getQuestionTypes().contains(v.getType()) || (f.getDifficulty() != null && f.getDifficulty() != v.getDifficulty())) { excluded.add(new Excluded(v.getId(), "Тип или сложность не соответствует игре", link)); continue; }
                if (!QuizContentEligibility.subjectComplete(v)) {
                    invalid++; excluded.add(new Excluded(v.getId(), "Неполный опубликованный вопрос: RU/KK, источник или ключ", link)); continue;
                }
                playable++;
            }
        }
        return new Coverage(Set.copyOf(required), published, audience, playable, invalid, List.copyOf(excluded));
    }

    /** Возвращает худшее покрытие допустимого состава комнаты и проверку опубликованности источника. */
    public Result diagnose(QuizGameForm f, Batch data) {
        List<String> errors = new ArrayList<>(); String source = "Источник не выбран", link = "/admin/quiz-games";
        if (f.getGameType() == QuizGameType.WORD_TRANSLATION) {
            VocabularySet set = data.wordSets().get(f.getVocabularySetId());
            if (set == null) errors.add("Выберите словарный набор");
            else {
                source = set.getCode(); link = "/admin/vocabulary?setId=" + set.getId();
                if (set.getStatus() == EditorialStatus.ARCHIVED || set.getPublishedVersion() == null
                        || set.getPublishedVersion().getStatus() != EditorialStatus.PUBLISHED || set.getPublishedVersion().getPublishedAt() == null) errors.add("Словарный набор не опубликован или архивирован");
                else if (set.getPublishedVersion().getReviewedAt() == null || QuizContentEligibility.blank(set.getPublishedVersion().getSourceReference())) errors.add("Публикация набора не подтверждена или не имеет источника");
                if (!Objects.equals(set.getSourceLanguage(), f.getSourceLanguage()) || !Objects.equals(set.getTargetLanguage(), f.getTargetLanguage())) errors.add("Направление игры не совпадает с набором");
            }
        } else if (f.getGameType() == QuizGameType.TRUE_FALSE) {
            FactSet set = data.factSets().get(f.getFactSetId());
            if (set == null) errors.add("Выберите набор утверждений");
            else { source = set.getTitleRu(); link = "/admin/facts?setId=" + set.getId();
                if (set.getStatus() != EditorialStatus.PUBLISHED) errors.add("Набор утверждений не опубликован или архивирован");
                if (QuizContentEligibility.blank(set.getSourcePolicy())) errors.add("У набора отсутствует политика источников");
            }
        } else if (f.getGameType() == QuizGameType.SUBJECT_QUIZ) {
            Subject subject = data.subjects().get(f.getSubjectId());
            if (subject == null) errors.add("Выберите предмет");
            else { source = subject.getTitleRu(); link = "/admin/questions?subjectId=" + subject.getId() + "&status=PUBLISHED"; }
            if (f.getQuestionTypes() == null || f.getQuestionTypes().isEmpty()) errors.add("Выберите хотя бы один предметный тип вопроса");
            if (f.getAudiencePolicy() == AudiencePolicy.UNIVERSAL) errors.add("Предметный банк не хранит явную универсальность; выберите политику классов");
        }
        List<Set<Long>> scenarios = scenarios(f, data.grades().keySet());
        List<Coverage> coverages = scenarios.stream().map(s -> coverage(f, s, data)).toList();
        Coverage worst = coverages.stream().min(Comparator.comparingInt(Coverage::playable)).orElse(new Coverage(Set.of(), 0, 0, 0, 0, List.of()));
        if (coverages.stream().anyMatch(c -> c.invalidPublished() > 0)) errors.add("В опубликованном составе есть неподтверждённые/неполные материалы; исправьте или архивируйте их");
        String audienceLabel = worst.requiredGrades().isEmpty() ? "Явная универсальная аудитория" : worst.requiredGrades().stream()
                .map(data.grades()::get).filter(Objects::nonNull).sorted(Comparator.comparing(Grade::getGradeNo))
                .map(Grade::getTitleRu).collect(Collectors.joining(", "));
        return new Result(source, link, List.copyOf(errors), worst, scenarios.size(), audienceLabel);
    }

    private List<Set<Long>> scenarios(QuizGameForm f, Set<Long> supported) {
        if (f.getAudiencePolicy() == AudiencePolicy.UNIVERSAL) return List.of(Set.of());
        List<Long> allowed = new ArrayList<>(f.getAudiencePolicy() == AudiencePolicy.FIXED_GRADES || f.getAudiencePolicy() == AudiencePolicy.ROOM_GRADE
                ? Objects.requireNonNullElse(f.getGradeIds(), List.of()) : supported);
        Collections.sort(allowed);
        if (allowed.isEmpty()) return List.of(Set.of());
        List<Set<Long>> result = new ArrayList<>();
        // Добавление требуемого класса только сужает выборку. Максимальные сочетания проверяют и все подмножества.
        int size = f.getAudiencePolicy() == AudiencePolicy.ROOM_GRADE ? 1 : Math.min(Math.max(1, f.getMaxPlayers()), allowed.size());
        combinations(allowed, size, 0, new LinkedHashSet<>(), result); return result;
    }
    private void combinations(List<Long> ids, int size, int start, Set<Long> selected, List<Set<Long>> result) {
        if (selected.size() == size) { result.add(Set.copyOf(selected)); return; }
        for (int i = start; i <= ids.size() - (size - selected.size()); i++) {
            selected.add(ids.get(i)); combinations(ids, size, i + 1, selected, result); selected.remove(ids.get(i));
        }
    }
    private static <T> Map<Long, T> index(List<T> values, Function<T, Long> key) { return values.stream().collect(Collectors.toMap(key, Function.identity())); }

    /** Пакет данных для нескольких карточек/сценариев в одной read-only транзакции. */
    public record Batch(VocabularyDistractorService.Dataset words, Map<Long, VocabularySet> wordSets,
                        Map<Long, FactSet> factSets, List<FactStatementVersion> facts, List<QuestionVersion> questions,
                        Map<Long, Subject> subjects, Map<Long, Grade> grades) {}
    /** Конкретная причина исключения и безопасная ссылка к административной форме. */
    public record Excluded(Long versionId, String reason, String editUrl) {}
    /** Статистика одной аудитории: число записей не подменяет число пригодных раундов. */
    public record Coverage(Set<Long> requiredGrades, int published, int audience, int playable, int invalidPublished, List<Excluded> excluded) {}
    /** Диагностика источника и худшего допустимого состава участников. */
    public record Result(String source, String bankUrl, List<String> errors, Coverage worst, int checkedScenarios, String audienceLabel) {}
}
