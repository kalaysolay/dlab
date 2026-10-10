package kz.damulab.quiz.catalog;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import kz.damulab.content.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Создание, проверка и публикация игр. Блокировка стабильной записи защищает смену указателей и revision. */
@Service
public class QuizGameAdminService {
    private final QuizGameRepository games;
    private final QuizGameVersionRepository versions;
    private final QuizWordGameConfigRepository wordConfigs;
    private final QuizSubjectGameConfigRepository subjectConfigs;
    private final QuizFactGameConfigRepository factConfigs;
    private final VocabularySetRepository wordSets;
    private final FactSetRepository factSets;
    private final SubjectRepository subjects;
    private final GradeRepository grades;
    private final QuizGameContentDiagnostics diagnostics;
    private final EditorialWorkflowService workflow;
    private final QuizGameVisibilityEventRepository visibilityEvents;

    public QuizGameAdminService(QuizGameRepository games, QuizGameVersionRepository versions,
            QuizWordGameConfigRepository wordConfigs, QuizSubjectGameConfigRepository subjectConfigs,
            QuizFactGameConfigRepository factConfigs, VocabularySetRepository wordSets, FactSetRepository factSets,
            SubjectRepository subjects, GradeRepository grades, QuizGameContentDiagnostics diagnostics,
            EditorialWorkflowService workflow, QuizGameVisibilityEventRepository visibilityEvents) {
        this.games = games; this.versions = versions; this.wordConfigs = wordConfigs; this.subjectConfigs = subjectConfigs;
        this.factConfigs = factConfigs; this.wordSets = wordSets; this.factSets = factSets; this.subjects = subjects;
        this.grades = grades; this.diagnostics = diagnostics; this.workflow = workflow; this.visibilityEvents = visibilityEvents;
    }

    /** Создаёт игру и типизированную черновую редакцию одной транзакцией; неполные переводы допустимы до review. */
    @Transactional
    public QuizGame create(QuizGameForm form, Long actorId) {
        normalizeCode(form); validateSave(form);
        if (games.findByCode(form.getCode()).isPresent()) throw new ContentValidationException("Код игры уже существует");
        QuizGame game = new QuizGame(form.getCode()); game.setCreatedBy(actorId); games.saveAndFlush(game);
        game.setDraftVersion(build(game, form, actorId)); game.advanceAdminRevision(); return game;
    }

    /** Сохраняет отдельную редакцию. Проверенная/опубликованная версия не изменяется, старая форма отклоняется. */
    @Transactional
    public void saveDraft(Long id, QuizGameForm form, Long actorId) {
        QuizGame game = lock(id, form.getExpectedRevision()); normalizeCode(form); validateSave(form);
        if (game.getPublishedVersion() != null && game.getPublishedVersion().getGameType() != form.getGameType()) throw new ContentValidationException("Тип опубликованной игры неизменяем");
        games.findByCode(form.getCode()).filter(g -> !g.getId().equals(id)).ifPresent(g -> { throw new ContentValidationException("Код игры уже существует"); });
        game.changeDraftCode(form.getCode()); game.setDraftVersion(build(game, form, actorId));
        if (game.getPublishedVersion() == null) game.setStatus(EditorialStatus.DRAFT);
        game.advanceAdminRevision();
    }

    /** Выполняет review/approve с аудитом и проверкой актуальности формы; публикация имеет отдельную команду. */
    @Transactional
    public void transition(Long id, long revision, String action, Long actorId) {
        QuizGame game = lock(id, revision); QuizGameVersion draft = requireDraft(game);
        if ("submit".equals(action)) {
            List<String> errors = QuizGameValidation.errors(form(game, draft, configs(List.of(draft))), supportedGrades(), true);
            if (!errors.isEmpty()) throw new ContentValidationException(errors);
            workflow.submit(draft, QuizContentType.GAME, id, actorId);
        } else if ("approve".equals(action)) workflow.approve(draft, QuizContentType.GAME, id, actorId);
        else throw new ContentValidationException("Неизвестное редакционное действие");
        if (game.getPublishedVersion() == null) game.setStatus(draft.getStatus());
        game.advanceAdminRevision();
    }

    /** Повторно проверяет текущую редакцию и банк в согласованной транзакции; предупреждения подтверждаются явно. */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void publish(Long id, long revision, boolean confirmWarnings, Long actorId) {
        QuizGame game = lock(id, revision); QuizGameVersion draft = requireDraft(game);
        View view = view(game, draft, configs(List.of(draft)), diagnostics.prepare());
        if (!view.errors().isEmpty()) throw new ContentValidationException(view.errors());
        if (!view.warnings().isEmpty() && !confirmWarnings) throw new ContentValidationException("Подтвердите предупреждения перед публикацией");
        // Проверка статуса выполняется сервером, даже если POST отправлен в обход disabled-кнопки.
        workflow.publish(draft, QuizContentType.GAME, id, actorId);
        game.setPublishedVersion(draft); game.setDraftVersion(null); game.setStatus(EditorialStatus.PUBLISHED); game.advanceAdminRevision();
    }

    /** Архивирует только стабильную игру; все версии и ссылки комнат остаются доступными для истории. */
    @Transactional
    public void archive(Long id, long revision, Long actorId) {
        QuizGame game = lock(id, revision);
        workflow.archive(game, QuizContentType.GAME, id, game.getPublishedVersion() == null ? null : game.getPublishedVersion().getId(), actorId);
        game.advanceAdminRevision();
    }

    /** Меняет видимость без новой учебной редакции, сохраняя полный журнал действий администратора. */
    @Transactional
    public void setVisibility(Long id, long revision, boolean visible, Long actorId) {
        QuizGame game = lock(id, revision);
        if (game.getPublishedVersion() == null) throw new ContentValidationException("Видимость можно изменить после первой публикации");
        if (game.isCatalogVisible() == visible) return;
        game.setCatalogVisible(visible); game.setVisibilityUpdatedBy(actorId); game.setVisibilityUpdatedAt(OffsetDateTime.now());
        visibilityEvents.save(new QuizGameVisibilityEvent(id, visible, actorId)); game.advanceAdminRevision();
    }

    /** Возвращает текущую административную редакцию, checklist и безопасный предпросмотр без создания комнаты. */
    @Transactional(readOnly = true)
    public View get(Long id) {
        QuizGame game = games.findAdminGame(id).orElseThrow(() -> new ContentValidationException("Игра не найдена"));
        QuizGameVersion shown = shown(game); return view(game, shown, configs(List.of(shown)), diagnostics.prepare());
    }

    /** Загружает список и диагностику пакетно; число SQL не растёт с количеством карточек. */
    @Transactional(readOnly = true)
    public List<View> list(String query, QuizGameType type, EditorialStatus status, String language, Boolean hasErrors) {
        List<QuizGame> all = games.findAdminGames(); if (all.isEmpty()) return List.of();
        List<QuizGameVersion> shown = all.stream().map(this::shown).toList();
        versions.findByIdIn(shown.stream().map(QuizGameVersion::getId).toList());
        Configs cfg = configs(shown); var data = diagnostics.prepare(); String q = safe(query).toLowerCase(Locale.ROOT);
        return all.stream().map(g -> view(g, shown(g), cfg, data))
                .filter(v -> q.isEmpty() || (v.game().getCode() + " " + v.form().getTitleRu() + " " + v.form().getTitleKk()).toLowerCase(Locale.ROOT).contains(q))
                .filter(v -> type == null || v.form().getGameType() == type)
                .filter(v -> status == null || v.game().getStatus() == status || v.version().getStatus() == status)
                .filter(v -> language == null || language.isBlank() || ("ru".equals(language) && !safe(v.form().getTitleRu()).isBlank()) || ("kk".equals(language) && !safe(v.form().getTitleKk()).isBlank()))
                .filter(v -> hasErrors == null || hasErrors == !v.errors().isEmpty()).toList();
    }

    private QuizGame lock(Long id, long revision) {
        QuizGame game = games.lockById(id).orElseThrow(() -> new ContentValidationException("Игра не найдена"));
        if (game.getAdminRevision() != revision) throw new ContentValidationException("Игра изменена другим действием. Обновите страницу и проверьте текущую редакцию");
        if (game.getStatus() == EditorialStatus.ARCHIVED) throw new ContentValidationException("Архивированную игру нельзя изменять или публиковать");
        return game;
    }
    private QuizGameVersion requireDraft(QuizGame game) { if (game.getDraftVersion() == null) throw new ContentValidationException("Нет черновика для редакционного действия"); return game.getDraftVersion(); }
    private QuizGameVersion shown(QuizGame game) {
        QuizGameVersion result = game.getDraftVersion() != null ? game.getDraftVersion() : game.getPublishedVersion();
        if (result == null) throw new ContentValidationException("У игры отсутствует редакция"); return result;
    }
    private void validateSave(QuizGameForm form) {
        List<String> errors = QuizGameValidation.errors(form, supportedGrades(), false);
        if (!errors.isEmpty()) throw new ContentValidationException(errors);
        // Даже черновик не может ссылаться на банк другого типа; источник может быть ещё не опубликован.
        switch (form.getGameType()) {
            case WORD_TRANSLATION -> {
                if (form.getVocabularySetId() == null) throw new ContentValidationException("Выберите словарный набор");
                VocabularySet set = wordSets.findById(form.getVocabularySetId()).orElseThrow(() -> new ContentValidationException("Набор не найден"));
                if (!set.getSourceLanguage().equals(form.getSourceLanguage()) || !set.getTargetLanguage().equals(form.getTargetLanguage())) throw new ContentValidationException("Направление игры не совпадает с набором");
            }
            case TRUE_FALSE -> { if (form.getFactSetId() == null || !factSets.existsById(form.getFactSetId())) throw new ContentValidationException("Выберите существующий набор утверждений"); }
            case SUBJECT_QUIZ -> {
                if (form.getSubjectId() == null || !subjects.existsById(form.getSubjectId())) throw new ContentValidationException("Выберите существующий предмет");
                if (form.getQuestionTypes() == null || form.getQuestionTypes().isEmpty()) throw new ContentValidationException("Выберите предметные типы вопросов");
            }
        }
    }
    private QuizGameVersion build(QuizGame game, QuizGameForm f, Long actor) {
        QuizGameVersion v = new QuizGameVersion(versions.findMaxVersionNo(game.getId()) + 1, game, f.getGameType(), safe(f.getTitleRu()), safe(f.getTitleKk()));
        v.setCreatedBy(actor); v.setDescriptionRu(safe(f.getDescriptionRu())); v.setDescriptionKk(safe(f.getDescriptionKk()));
        v.setMechanicLabelRu(safe(f.getMechanicLabelRu())); v.setMechanicLabelKk(safe(f.getMechanicLabelKk()));
        v.setCardStyle(f.getCardStyle()); v.setCatalogOrder(f.getCatalogOrder()); v.setGradeSelectionMode(f.getGradeSelectionMode()); v.setAudiencePolicy(f.getAudiencePolicy());
        v.setDefaultQuestionCount(f.getDefaultQuestionCount()); v.setMinQuestionCount(f.getMinQuestionCount()); v.setMaxQuestionCount(f.getMaxQuestionCount());
        v.setDefaultRoundSeconds(f.getDefaultRoundSeconds()); v.setMinRoundSeconds(f.getMinRoundSeconds()); v.setMaxRoundSeconds(f.getMaxRoundSeconds());
        v.setMaxPlayers(f.getMaxPlayers()); v.setQuestionCountEditable(f.isQuestionCountEditable()); v.setRoundSecondsEditable(f.isRoundSecondsEditable());
        v.setGradeIds(new LinkedHashSet<>(f.getGradeIds())); versions.saveAndFlush(v);
        switch (f.getGameType()) {
            case WORD_TRANSLATION -> wordConfigs.save(new QuizWordGameConfig(v, wordSets.getReferenceById(f.getVocabularySetId()), f.getSourceLanguage(), f.getTargetLanguage()));
            case TRUE_FALSE -> factConfigs.save(new QuizFactGameConfig(v, factSets.getReferenceById(f.getFactSetId())));
            case SUBJECT_QUIZ -> { QuizSubjectGameConfig config = new QuizSubjectGameConfig(v, subjects.getReferenceById(f.getSubjectId()), f.getDifficulty()); config.setQuestionTypes(f.getQuestionTypes()); subjectConfigs.save(config); }
        }
        return v;
    }
    private View view(QuizGame game, QuizGameVersion v, Configs configs, QuizGameContentDiagnostics.Batch data) {
        QuizGameForm form = form(game, v, configs); var content = diagnostics.diagnose(form, data);
        List<String> errors = new ArrayList<>(QuizGameValidation.errors(form, data.grades().keySet(), true)); errors.addAll(content.errors());
        if (game.getStatus() == EditorialStatus.ARCHIVED) errors.add("Игра архивирована");
        if (content.worst().playable() < form.getMinQuestionCount()) errors.add("Пригодных раундов " + content.worst().playable() + "; минимум " + form.getMinQuestionCount());
        List<String> warnings = new ArrayList<>();
        if (content.worst().playable() < form.getDefaultQuestionCount()) warnings.add("Контента меньше значения раундов по умолчанию; фактический старт может быть недоступен");
        if (content.worst().playable() < form.getMaxQuestionCount()) warnings.add("Контента недостаточно для максимального количества раундов");
        if (safe(form.getDescriptionRu()).isBlank() || safe(form.getDescriptionKk()).isBlank()) warnings.add("Описание карточки заполнено не на обоих языках");
        return new View(game, v, form, content, List.copyOf(errors), List.copyOf(warnings));
    }
    private Configs configs(List<QuizGameVersion> list) {
        List<Long> ids = list.stream().map(QuizGameVersion::getId).toList();
        return new Configs(index(wordConfigs.findByGameVersionIdIn(ids), QuizWordGameConfig::getGameVersionId),
                index(subjectConfigs.findByGameVersionIdIn(ids), QuizSubjectGameConfig::getGameVersionId),
                index(factConfigs.findByGameVersionIdIn(ids), QuizFactGameConfig::getGameVersionId));
    }
    private QuizGameForm form(QuizGame game, QuizGameVersion v, Configs cfg) {
        QuizGameForm f = new QuizGameForm(); f.setCode(game.getCode()); f.setExpectedRevision(game.getAdminRevision()); f.setGameType(v.getGameType());
        f.setTitleRu(v.getTitleRu()); f.setTitleKk(v.getTitleKk()); f.setDescriptionRu(v.getDescriptionRu()); f.setDescriptionKk(v.getDescriptionKk());
        f.setMechanicLabelRu(v.getMechanicLabelRu()); f.setMechanicLabelKk(v.getMechanicLabelKk()); f.setCardStyle(v.getCardStyle()); f.setCatalogOrder(v.getCatalogOrder());
        f.setGradeSelectionMode(v.getGradeSelectionMode()); f.setAudiencePolicy(v.getAudiencePolicy()); f.setGradeIds(new ArrayList<>(v.getGradeIds()));
        f.setDefaultQuestionCount(v.getDefaultQuestionCount()); f.setMinQuestionCount(v.getMinQuestionCount()); f.setMaxQuestionCount(v.getMaxQuestionCount());
        f.setDefaultRoundSeconds(v.getDefaultRoundSeconds()); f.setMinRoundSeconds(v.getMinRoundSeconds()); f.setMaxRoundSeconds(v.getMaxRoundSeconds()); f.setMaxPlayers(v.getMaxPlayers());
        f.setQuestionCountEditable(v.isQuestionCountEditable()); f.setRoundSecondsEditable(v.isRoundSecondsEditable());
        QuizWordGameConfig word = cfg.words().get(v.getId()); if (word != null) { f.setVocabularySetId(word.getVocabularySet().getId()); f.setSourceLanguage(word.getSourceLanguage()); f.setTargetLanguage(word.getTargetLanguage()); }
        QuizFactGameConfig fact = cfg.facts().get(v.getId()); if (fact != null) f.setFactSetId(fact.getFactSet().getId());
        QuizSubjectGameConfig subject = cfg.subjects().get(v.getId()); if (subject != null) { f.setSubjectId(subject.getSubject().getId()); f.setDifficulty(subject.getDifficulty()); f.setQuestionTypes(new LinkedHashSet<>(subject.getQuestionTypes())); }
        return f;
    }
    private Set<Long> supportedGrades() { return grades.findAllByOrderByGradeNoAsc().stream().map(Grade::getId).collect(Collectors.toSet()); }
    private static void normalizeCode(QuizGameForm f) { f.setCode(safe(f.getCode()).toLowerCase(Locale.ROOT)); }
    private static String safe(String v) { return v == null ? "" : v.strip(); }
    private static <T> Map<Long, T> index(List<T> values, Function<T, Long> key) { return values.stream().collect(Collectors.toMap(key, Function.identity())); }
    private record Configs(Map<Long, QuizWordGameConfig> words, Map<Long, QuizSubjectGameConfig> subjects, Map<Long, QuizFactGameConfig> facts) {}

    /** Модель административной страницы; правильные ответы и реальные комнаты в неё не входят. */
    public record View(QuizGame game, QuizGameVersion version, QuizGameForm form, QuizGameContentDiagnostics.Result content,
                       List<String> errors, List<String> warnings) {}
}
