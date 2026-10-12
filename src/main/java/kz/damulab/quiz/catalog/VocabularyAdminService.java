package kz.damulab.quiz.catalog;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Редакционный workflow наборов и слов: версии, публикационные проверки, аудит и поиск дублей. */
@Service
public class VocabularyAdminService {
    private final VocabularySetRepository sets;
    private final VocabularySetVersionRepository setVersions;
    private final VocabularyItemRepository items;
    private final VocabularyItemVersionRepository versions;
    private final VocabularyItemAnswerRepository answers;
    private final QuizTextNormalizer normalizer;
    private final EditorialWorkflowService workflow;

    public VocabularyAdminService(VocabularySetRepository sets, VocabularySetVersionRepository setVersions,
            VocabularyItemRepository items, VocabularyItemVersionRepository versions,
            VocabularyItemAnswerRepository answers, QuizTextNormalizer normalizer,
            EditorialWorkflowService workflow) {
        this.sets = sets; this.setVersions = setVersions; this.items = items; this.versions = versions;
        this.answers = answers; this.normalizer = normalizer; this.workflow = workflow;
    }

    /** Возвращает наборы для административного списка. */
    public List<VocabularySet> listSets() { return sets.findAllByOrderByIdAsc(); }
    /** Возвращает стабильные учебные значения вместе с текущими указателями версий. */
    public List<VocabularyItem> listItems() { return items.findAllByOrderByIdAsc(); }
    /** Загружает набор или выдаёт безопасную бизнес-ошибку. */
    public VocabularySet requireSet(Long id) { return sets.findById(id).orElseThrow(() -> new ContentValidationException("Набор слов не найден")); }
    /** Загружает слово или выдаёт безопасную бизнес-ошибку. */
    public VocabularyItem requireItem(Long id) { return items.findById(id).orElseThrow(() -> new ContentValidationException("Слово не найдено")); }

    /** Возвращает текущую черновую или опубликованную редакцию набора для формы. */
    @Transactional(readOnly = true)
    public VocabularySetForm setForm(Long setId) {
        VocabularySet set = requireSet(setId); VocabularySetVersion v = set.getDraftVersion() != null ? set.getDraftVersion() : set.getPublishedVersion();
        VocabularySetForm form = new VocabularySetForm(); form.setCode(set.getCode()); form.setSourceLanguage(set.getSourceLanguage()); form.setTargetLanguage(set.getTargetLanguage());
        if (v != null) { form.setTitleRu(v.getTitleRu()); form.setTitleKk(v.getTitleKk()); form.setSourceReference(v.getSourceReference()); form.setCrossSetDistractorsAllowed(v.isCrossSetDistractorsAllowed()); }
        return form;
    }

    /** Создаёт стабильный набор и его первую черновую редакцию. */
    @Transactional
    public VocabularySet createSet(VocabularySetForm form, Long actorId) {
        validateSet(form);
        String code = form.getCode().strip();
        if (sets.existsByCodeIgnoreCase(code)) throw new ContentValidationException("Код набора уже используется");
        VocabularySet set = new VocabularySet(code, form.getSourceLanguage(), form.getTargetLanguage());
        set.setCreatedBy(actorId); sets.save(set);
        VocabularySetVersion version = new VocabularySetVersion(1, set, form.getTitleRu().strip(), form.getTitleKk().strip(), form.getSourceReference().strip());
        version.setCrossSetDistractorsAllowed(form.isCrossSetDistractorsAllowed()); version.setCreatedBy(actorId);
        setVersions.save(version); set.setDraftVersion(version);
        return sets.save(set);
    }

    /** Сохраняет черновик набора; после публикации всегда создаётся новая редакция. */
    @Transactional
    public VocabularySetVersion saveSetDraft(Long setId, VocabularySetForm form, Long actorId) {
        validateSet(form);
        VocabularySet set = requireSet(setId);
        VocabularySetVersion draft = set.getDraftVersion();
        if (draft == null || draft.getStatus() != EditorialStatus.DRAFT) {
            draft = new VocabularySetVersion(setVersions.findMaxVersionNo(setId) + 1, set,
                    form.getTitleRu().strip(), form.getTitleKk().strip(), form.getSourceReference().strip());
            draft.setCreatedBy(actorId);
        } else {
            draft.setTitleRu(form.getTitleRu().strip()); draft.setTitleKk(form.getTitleKk().strip());
            draft.setSourceReference(form.getSourceReference().strip());
        }
        draft.setCrossSetDistractorsAllowed(form.isCrossSetDistractorsAllowed()); setVersions.save(draft);
        set.setDraftVersion(draft); sets.save(set); return draft;
    }

    /** Создаёт самостоятельное учебное значение; совпадающее написание другого значения разрешено. */
    @Transactional
    public VocabularyItem createItem(VocabularyItemForm form, Long actorId) {
        validateAnswerForm(form);
        VocabularySet set = requireSet(form.getSetId());
        VocabularyItem item = new VocabularyItem(set); item.setCreatedBy(actorId); items.save(item);
        VocabularyItemVersion version = buildVersion(1, item, form, actorId);
        versions.save(version); saveAnswers(version, form); item.setDraftVersion(version); return items.save(item);
    }

    /** Сохраняет редактируемую версию, не затрагивая текущую публикацию и её переводы. */
    @Transactional
    public VocabularyItemVersion saveItemDraft(Long itemId, VocabularyItemForm form, Long actorId) {
        validateAnswerForm(form);
        VocabularyItem item = requireItem(itemId);
        if (!item.getSet().getId().equals(form.getSetId())) throw new ContentValidationException("Нельзя перенести значение в другой набор");
        // Каждое сохранение после отправки/публикации создаёт новую версию; обычный DRAFT можно редактировать полями,
        // но переводы всё равно проще и безопаснее сохранить новой редакцией без удаления строк истории.
        VocabularyItemVersion version = buildVersion(versions.findMaxVersionNo(itemId) + 1, item, form, actorId);
        versions.save(version); saveAnswers(version, form); item.setDraftVersion(version); items.save(item); return version;
    }

    /** Ищет вероятные дубли по исходному слову и переводам, не запрещая омонимы. */
    @Transactional(readOnly = true)
    public List<VocabularyItemVersion> findPotentialDuplicates(Long setId, String query) {
        String normalized = normalizer.normalize(query);
        if (normalized.isBlank()) return List.of();
        Map<Long, VocabularyItemVersion> newestByMeaning = new LinkedHashMap<>();
        for (VocabularyItemVersion candidate : versions.findPotentialDuplicates(setId, normalized)) {
            newestByMeaning.merge(candidate.getItem().getId(), candidate,
                    (left, right) -> left.getVersionNo() >= right.getVersionNo() ? left : right);
        }
        return List.copyOf(newestByMeaning.values());
    }

    /** Массово отправляет только валидные DRAFT-редакции; ошибка откатывает всю операцию. */
    @Transactional
    public void submitItems(Collection<Long> itemIds, Long actorId) {
        if (itemIds == null || itemIds.isEmpty()) throw new ContentValidationException("Выберите хотя бы одно слово");
        for (Long itemId : new LinkedHashSet<>(itemIds)) submitItem(itemId, actorId);
    }

    /** Отправляет одно слово на проверку после полной публикационной валидации. */
    @Transactional
    public void submitItem(Long itemId, Long actorId) {
        VocabularyItem item = requireItem(itemId); VocabularyItemVersion draft = requireDraft(item);
        validateItemForPublication(draft); workflow.submit(draft, QuizContentType.VOCABULARY_ITEM, itemId, actorId);
        if (item.getPublishedVersion() == null) item.setStatus(EditorialStatus.NEEDS_REVIEW);
    }

    /** Одобряет проверенную редакцию и записывает проверяющего. */
    @Transactional
    public void approveItem(Long itemId, Long actorId) {
        VocabularyItem item = requireItem(itemId); VocabularyItemVersion draft = requireDraft(item);
        validateItemForPublication(draft); workflow.approve(draft, QuizContentType.VOCABULARY_ITEM, itemId, actorId);
        if (item.getPublishedVersion() == null) item.setStatus(EditorialStatus.APPROVED);
    }

    /** Публикует качественную редакцию независимо от наличия трёх дистракторов в ещё пустом банке. */
    @Transactional
    public void publishItem(Long itemId, Long actorId) {
        VocabularyItem item = requireItem(itemId); VocabularyItemVersion draft = requireDraft(item);
        validateItemForPublication(draft); workflow.publish(draft, QuizContentType.VOCABULARY_ITEM, itemId, actorId);
        item.setPublishedVersion(draft); item.setDraftVersion(null); item.setStatus(EditorialStatus.PUBLISHED);
    }

    /** Архивирует стабильное слово без удаления опубликованных и черновых версий. */
    @Transactional
    public void archiveItem(Long itemId, Long actorId) {
        VocabularyItem item = requireItem(itemId);
        workflow.archive(item, QuizContentType.VOCABULARY_ITEM, itemId,
                item.getPublishedVersion() == null ? null : item.getPublishedVersion().getId(), actorId);
    }

    /** Отправляет метаданные набора на проверку. */
    @Transactional
    public void submitSet(Long setId, Long actorId) { VocabularySet set = requireSet(setId); VocabularySetVersion v = requireDraft(set); validateSetVersion(v); workflow.submit(v, QuizContentType.VOCABULARY_SET, setId, actorId); if (set.getPublishedVersion() == null) set.setStatus(EditorialStatus.NEEDS_REVIEW); }
    /** Одобряет метаданные набора и сохраняет audit проверяющего. */
    @Transactional
    public void approveSet(Long setId, Long actorId) { VocabularySet set = requireSet(setId); VocabularySetVersion v = requireDraft(set); validateSetVersion(v); workflow.approve(v, QuizContentType.VOCABULARY_SET, setId, actorId); if (set.getPublishedVersion() == null) set.setStatus(EditorialStatus.APPROVED); }
    /** Публикует одобренную редакцию набора атомарной сменой указателя. */
    @Transactional
    public void publishSet(Long setId, Long actorId) { VocabularySet set = requireSet(setId); VocabularySetVersion v = requireDraft(set); validateSetVersion(v); workflow.publish(v, QuizContentType.VOCABULARY_SET, setId, actorId); set.setPublishedVersion(v); set.setDraftVersion(null); set.setStatus(EditorialStatus.PUBLISHED); }
    /** Архивирует набор без удаления его версий и слов. */
    @Transactional
    public void archiveSet(Long setId, Long actorId) { VocabularySet set = requireSet(setId); workflow.archive(set, QuizContentType.VOCABULARY_SET, setId, set.getPublishedVersion() == null ? null : set.getPublishedVersion().getId(), actorId); }

    /** Собирает форму из draft, а при его отсутствии — из текущей публикации. */
    @Transactional(readOnly = true)
    public VocabularyItemForm form(Long itemId) {
        VocabularyItem item = requireItem(itemId); VocabularyItemVersion v = item.getDraftVersion() != null ? item.getDraftVersion() : item.getPublishedVersion();
        VocabularyItemForm f = new VocabularyItemForm(); f.setSetId(item.getSet().getId());
        if (v == null) return f;
        f.setSourceText(v.getSourceText()); f.setPronunciation(v.getPronunciation()); f.setPartOfSpeech(v.getPartOfSpeech());
        f.setMeaningNoteRu(v.getMeaningNoteRu()); f.setMeaningNoteKk(v.getMeaningNoteKk()); f.setDifficulty(v.getDifficulty());
        f.setSourceReference(v.getSourceReference()); f.setUniversalAudience(v.isUniversalAudience()); f.setGradeIds(new ArrayList<>(v.getGradeIds()));
        List<VocabularyItemAnswer> list = answers.findByItemVersionIdIn(List.of(v.getId()));
        f.setPrimaryAnswer(list.stream().filter(VocabularyItemAnswer::isPrimary).map(VocabularyItemAnswer::getAnswerText).findFirst().orElse(""));
        f.setAlternativeAnswers(list.stream().filter(a -> !a.isPrimary()).map(VocabularyItemAnswer::getAnswerText).collect(Collectors.joining("\n")));
        return f;
    }

    private VocabularyItemVersion buildVersion(int number, VocabularyItem item, VocabularyItemForm form, Long actorId) {
        VocabularyItemVersion v = new VocabularyItemVersion(number, item, safe(form.getSourceText()), normalizer.normalize(form.getSourceText()), safe(form.getSourceReference()));
        v.setPronunciation(blankToNull(form.getPronunciation())); v.setPartOfSpeech(blankToNull(form.getPartOfSpeech()));
        v.setMeaningNoteRu(blankToNull(form.getMeaningNoteRu())); v.setMeaningNoteKk(blankToNull(form.getMeaningNoteKk()));
        v.setDifficulty(form.getDifficulty()); v.setUniversalAudience(form.isUniversalAudience()); v.setGradeIds(new LinkedHashSet<>(form.getGradeIds())); v.setCreatedBy(actorId);
        return v;
    }

    private void saveAnswers(VocabularyItemVersion version, VocabularyItemForm form) {
        validateAnswerForm(form);
        List<VocabularyItemAnswer> result = new ArrayList<>();
        result.add(new VocabularyItemAnswer(version, safe(form.getPrimaryAnswer()), normalizer.normalize(form.getPrimaryAnswer()), true));
        if (form.getAlternativeAnswers() != null) for (String text : form.getAlternativeAnswers().split("\\R")) {
            if (!text.isBlank()) result.add(new VocabularyItemAnswer(version, text.strip(), normalizer.normalize(text), false));
        }
        answers.saveAll(result);
    }

    /** Проверяет единственный primary и дубли до обращения к DB unique, чтобы форма получила понятную ошибку. */
    private void validateAnswerForm(VocabularyItemForm form) {
        String primary = normalizer.normalize(form.getPrimaryAnswer());
        if (primary.isBlank()) throw new ContentValidationException("Укажите один основной перевод");
        Set<String> normalized = new HashSet<>(); normalized.add(primary);
        if (form.getAlternativeAnswers() != null) for (String text : form.getAlternativeAnswers().split("\\R")) {
            if (!text.isBlank() && !normalized.add(normalizer.normalize(text))) {
                throw new ContentValidationException("Переводы должны различаться после нормализации");
            }
        }
    }

    private void validateItemForPublication(VocabularyItemVersion v) {
        List<String> errors = new ArrayList<>();
        if (v.getSourceText() == null || v.getSourceText().isBlank()) errors.add("Укажите слово");
        if (!normalizer.normalize(v.getSourceText()).equals(v.getSourceNormalized())) errors.add("Нормализованная форма слова устарела");
        if (v.getSourceReference() == null || v.getSourceReference().isBlank()) errors.add("Укажите проверяемый источник");
        if ((v.getMeaningNoteRu() == null || v.getMeaningNoteRu().isBlank()) && (v.getMeaningNoteKk() == null || v.getMeaningNoteKk().isBlank())) errors.add("Опишите одно учебное значение слова");
        if (v.getDifficulty() < 1 || v.getDifficulty() > 5) errors.add("Сложность должна быть от 1 до 5");
        if (v.isUniversalAudience() == !v.getGradeIds().isEmpty()) errors.add("Выберите явную универсальную аудиторию или хотя бы один класс");
        List<VocabularyItemAnswer> list = answers.findByItemVersionIdIn(List.of(v.getId()));
        if (list.stream().filter(VocabularyItemAnswer::isPrimary).count() != 1 || list.stream().filter(VocabularyItemAnswer::isPrimary).anyMatch(a -> a.getAnswerNormalized().isBlank())) errors.add("Нужен ровно один непустой основной перевод");
        Set<String> unique = new HashSet<>(); if (list.stream().anyMatch(a -> a.getAnswerNormalized().isBlank() || !unique.add(a.getAnswerNormalized()))) errors.add("Переводы должны быть непустыми и различаться после нормализации");
        if (!errors.isEmpty()) throw new ContentValidationException(errors);
    }

    private void validateSet(VocabularySetForm f) {
        if (safe(f.getCode()).isBlank() || !safe(f.getCode()).matches("[A-Za-z0-9_-]{2,96}")) throw new ContentValidationException("Код набора: 2–96 латинских букв, цифр, _ или -");
        boolean direction = (Set.of("en", "kk").contains(f.getSourceLanguage()) && "ru".equals(f.getTargetLanguage())) || ("ru".equals(f.getSourceLanguage()) && Set.of("en", "kk").contains(f.getTargetLanguage()));
        if (!direction) throw new ContentValidationException("Поддерживаются направления en→ru, ru→en, kk→ru и ru→kk");
        if (safe(f.getTitleRu()).isBlank() || safe(f.getTitleKk()).isBlank() || safe(f.getSourceReference()).isBlank()) throw new ContentValidationException("Заполните оба названия и источник набора");
    }
    private void validateSetVersion(VocabularySetVersion v) { if (safe(v.getTitleRu()).isBlank() || safe(v.getTitleKk()).isBlank() || safe(v.getSourceReference()).isBlank()) throw new ContentValidationException("Заполните оба названия и источник набора"); }
    private VocabularyItemVersion requireDraft(VocabularyItem item) { if (item.getDraftVersion() == null) throw new ContentValidationException("У слова нет черновой редакции"); return item.getDraftVersion(); }
    private VocabularySetVersion requireDraft(VocabularySet set) { if (set.getDraftVersion() == null) throw new ContentValidationException("У набора нет черновой редакции"); return set.getDraftVersion(); }
    private static String safe(String v) { return v == null ? "" : v.strip(); }
    private static String blankToNull(String v) { return v == null || v.isBlank() ? null : v.strip(); }
}
