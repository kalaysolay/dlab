package kz.damulab.quiz.catalog;

import java.util.*;
import kz.damulab.content.GradeRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** SSR-страницы банка слов. Все изменения выполняются POST с глобальной ADMIN/CSRF-защитой. */
@Controller
@RequestMapping("/admin/vocabulary")
public class VocabularyAdminPageController {
    private final VocabularyAdminService vocabulary;
    private final VocabularyDistractorService distractors;
    private final VocabularyItemAnswerRepository answers;
    private final GradeRepository grades;
    private final CurrentAdminService currentAdmin;

    public VocabularyAdminPageController(VocabularyAdminService vocabulary, VocabularyDistractorService distractors,
            VocabularyItemAnswerRepository answers, GradeRepository grades, CurrentAdminService currentAdmin) {
        this.vocabulary = vocabulary; this.distractors = distractors; this.answers = answers;
        this.grades = grades; this.currentAdmin = currentAdmin;
    }

    /** Показывает настройки наборов отдельно от списка слов. */
    @GetMapping("/sets")
    public String sets(Model model) {
        model.addAttribute("activeAdminNav", "vocabularySets");
        model.addAttribute("sets", vocabulary.listSets());
        model.addAttribute("setForm", new VocabularySetForm());
        return "admin/vocabulary-sets";
    }

    /** Показывает фильтруемый список слов; прежний адрес банка остаётся доступным. */
    @GetMapping({"", "/items"})
    public String list(@RequestParam(required = false) Long setId,
                       @RequestParam(required = false) EditorialStatus status,
                       @RequestParam(required = false) String sourceLanguage,
                       @RequestParam(required = false) String targetLanguage,
                       @RequestParam(required = false) Long gradeId,
                       @RequestParam(required = false) Integer difficulty,
                       @RequestParam(defaultValue = "") String query, Model model) {
        List<VocabularyItem> filtered = vocabulary.listItems().stream()
                .filter(i -> setId == null || i.getSet().getId().equals(setId))
                .filter(i -> sourceLanguage == null || sourceLanguage.isBlank() || i.getSet().getSourceLanguage().equals(sourceLanguage))
                .filter(i -> targetLanguage == null || targetLanguage.isBlank() || i.getSet().getTargetLanguage().equals(targetLanguage))
                .filter(i -> status == null || i.getStatus() == status || (i.getDraftVersion() != null && i.getDraftVersion().getStatus() == status))
                .filter(i -> difficulty == null || shownVersion(i) != null && shownVersion(i).getDifficulty() == difficulty)
                .filter(i -> gradeId == null || shownVersion(i) != null && (shownVersion(i).isUniversalAudience() || shownVersion(i).getGradeIds().contains(gradeId)))
                .filter(i -> matches(i, query)).toList();
        Map<Long, String> primary = new HashMap<>();
        List<Long> versionIds = filtered.stream().map(this::shownVersion).filter(Objects::nonNull).map(VocabularyItemVersion::getId).toList();
        answers.findByItemVersionIdIn(versionIds).stream().filter(VocabularyItemAnswer::isPrimary)
                .forEach(a -> primary.put(a.getItemVersion().getId(), a.getAnswerText()));
        model.addAttribute("activeAdminNav", "vocabulary"); model.addAttribute("sets", vocabulary.listSets());
        model.addAttribute("items", filtered); model.addAttribute("primaryAnswers", primary);
        model.addAttribute("selectedSetId", setId); model.addAttribute("selectedStatus", status); model.addAttribute("query", query);
        model.addAttribute("selectedSourceLanguage", sourceLanguage); model.addAttribute("selectedTargetLanguage", targetLanguage);
        model.addAttribute("selectedGradeId", gradeId); model.addAttribute("selectedDifficulty", difficulty);
        model.addAttribute("grades", grades.findAllByOrderByGradeNoAsc());
        model.addAttribute("statuses", EditorialStatus.values());
        return "admin/vocabulary-bank";
    }

    /** Открывает пустую форму нового учебного значения. */
    @GetMapping("/items/new")
    public String newItem(@RequestParam(required = false) Long setId, Model model) {
        VocabularyItemForm form = new VocabularyItemForm(); form.setSetId(setId); return form(model, form, null);
    }

    /** Открывает draft, а при его отсутствии — опубликованную версию только как основу нового черновика. */
    @GetMapping("/items/{id}/edit")
    public String editItem(@PathVariable Long id, Model model) { return form(model, vocabulary.form(id), id); }

    /** Открывает настройки набора; после публикации сохранение создаёт новую редакцию. */
    @GetMapping("/sets/{id}/edit")
    public String editSet(@PathVariable Long id, Model model) {
        model.addAttribute("activeAdminNav", "vocabularySets"); model.addAttribute("setId", id);
        model.addAttribute("set", vocabulary.requireSet(id)); model.addAttribute("setForm", vocabulary.setForm(id));
        return "admin/vocabulary-set-form";
    }

    /** Сохраняет новую или текущую draft-редакцию метаданных набора. */
    @PostMapping("/sets/{id}")
    public String saveSet(@PathVariable Long id, @ModelAttribute("setForm") VocabularySetForm form,
                          Authentication auth, Model model, RedirectAttributes redirect) {
        try { vocabulary.saveSetDraft(id, form, currentAdmin.requireId(auth)); redirect.addFlashAttribute("successMessage", "Редакция набора сохранена"); return "redirect:/admin/vocabulary/sets/" + id + "/edit"; }
        catch (ContentValidationException ex) { model.addAttribute("formErrors", ex.getErrors()); model.addAttribute("activeAdminNav", "vocabularySets"); model.addAttribute("setId", id); model.addAttribute("set", vocabulary.requireSet(id)); return "admin/vocabulary-set-form"; }
    }

    /** Создаёт слово; неполный черновик допустим, публикационные проверки выполняются при submit/publish. */
    @PostMapping("/items")
    public String createItem(@ModelAttribute("itemForm") VocabularyItemForm form, Authentication auth,
                             Model model, RedirectAttributes redirect) {
        try { VocabularyItem item = vocabulary.createItem(form, currentAdmin.requireId(auth)); redirect.addFlashAttribute("successMessage", "Черновик слова создан"); return "redirect:/admin/vocabulary/items/" + item.getId() + "/edit"; }
        catch (ContentValidationException ex) { model.addAttribute("formErrors", ex.getErrors()); return form(model, form, null); }
    }

    /** Сохраняет новую редакцию и не изменяет текущую публикацию. */
    @PostMapping("/items/{id}")
    public String saveItem(@PathVariable Long id, @ModelAttribute("itemForm") VocabularyItemForm form,
                           Authentication auth, Model model, RedirectAttributes redirect) {
        try { vocabulary.saveItemDraft(id, form, currentAdmin.requireId(auth)); redirect.addFlashAttribute("successMessage", "Новая редакция сохранена"); return "redirect:/admin/vocabulary/items/" + id + "/edit"; }
        catch (ContentValidationException ex) { model.addAttribute("formErrors", ex.getErrors()); return form(model, form, id); }
    }

    /** Создаёт новый набор с отдельной версией настроек. */
    @PostMapping("/sets")
    public String createSet(@ModelAttribute VocabularySetForm setForm, Authentication auth, RedirectAttributes redirect) {
        return run(redirect, () -> vocabulary.createSet(setForm, currentAdmin.requireId(auth)), "Набор слов создан", "/admin/vocabulary/sets");
    }

    /** Выполняет разрешённое редакционное действие над набором только через POST. */
    @PostMapping("/sets/{id}/{action}")
    public String setAction(@PathVariable Long id, @PathVariable String action, Authentication auth, RedirectAttributes redirect) {
        return run(redirect, () -> { Long actor = currentAdmin.requireId(auth); switch (action) {
            case "submit" -> vocabulary.submitSet(id, actor); case "approve" -> vocabulary.approveSet(id, actor);
            case "publish" -> vocabulary.publishSet(id, actor); case "archive" -> vocabulary.archiveSet(id, actor);
            default -> throw new ContentValidationException("Неизвестное действие"); } }, "Статус набора обновлён", "/admin/vocabulary/sets");
    }

    /** Выполняет редакционный переход слова; GET-маршрутов для изменений нет. */
    @PostMapping("/items/{id}/{action}")
    public String itemAction(@PathVariable Long id, @PathVariable String action, Authentication auth, RedirectAttributes redirect) {
        return run(redirect, () -> { Long actor = currentAdmin.requireId(auth); switch (action) {
            case "submit" -> vocabulary.submitItem(id, actor); case "approve" -> vocabulary.approveItem(id, actor);
            case "publish" -> vocabulary.publishItem(id, actor); case "archive" -> vocabulary.archiveItem(id, actor);
            default -> throw new ContentValidationException("Неизвестное действие"); } }, "Статус слова обновлён");
    }

    /** Запрещает двусмысленную пару для текущей редакции слова. */
    @PostMapping("/items/{id}/exclusions")
    public String exclude(@PathVariable Long id, @RequestParam Long excludedVersionId, @RequestParam String reason,
                          Authentication auth, RedirectAttributes redirect) {
        try {
            VocabularyItem item = vocabulary.requireItem(id); VocabularyItemVersion source = shownVersion(item);
            if (source == null) throw new ContentValidationException("У слова нет редакции");
            distractors.exclude(source.getId(), excludedVersionId, reason, currentAdmin.requireId(auth));
            redirect.addFlashAttribute("successMessage", "Пара дистракторов запрещена");
        } catch (ContentValidationException ex) { redirect.addFlashAttribute("errorMessage", String.join("; ", ex.getErrors())); }
        return "redirect:/admin/vocabulary/items/" + id + "/edit";
    }

    /** Массовая отправка сохраняет те же проверки, что одиночный переход. */
    @PostMapping("/items/bulk-submit")
    public String bulkSubmit(@RequestParam(name = "itemIds", required = false) List<Long> itemIds,
                             Authentication auth, RedirectAttributes redirect) {
        return run(redirect, () -> vocabulary.submitItems(itemIds, currentAdmin.requireId(auth)), "Выбранные слова отправлены на проверку");
    }

    private String form(Model model, VocabularyItemForm form, Long id) {
        model.addAttribute("activeAdminNav", "vocabulary"); model.addAttribute("itemForm", form);
        model.addAttribute("itemId", id); model.addAttribute("editing", id != null); model.addAttribute("sets", vocabulary.listSets());
        model.addAttribute("grades", grades.findAllByOrderByGradeNoAsc());
        if (id != null) {
            VocabularyItem item = vocabulary.requireItem(id); VocabularyItemVersion shown = shownVersion(item);
            model.addAttribute("item", item);
            if (shown != null) model.addAttribute("diagnostics", distractors.diagnose(shown.getId(), shown.getGradeIds()));
            model.addAttribute("duplicates", vocabulary.findPotentialDuplicates(item.getSet().getId(), form.getSourceText()).stream().filter(v -> !v.getItem().getId().equals(id)).toList());
        } else model.addAttribute("duplicates", vocabulary.findPotentialDuplicates(form.getSetId(), form.getSourceText()));
        return "admin/vocabulary-form";
    }

    private boolean matches(VocabularyItem item, String query) {
        if (query == null || query.isBlank()) return true; VocabularyItemVersion v = shownVersion(item);
        return v != null && (v.getSourceText().toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT))
                || answers.findByItemVersionIdIn(List.of(v.getId())).stream().anyMatch(a -> a.getAnswerText().toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT))));
    }
    private VocabularyItemVersion shownVersion(VocabularyItem item) { return item.getDraftVersion() != null ? item.getDraftVersion() : item.getPublishedVersion(); }
    private String run(RedirectAttributes redirect, Runnable action, String success) {
        return run(redirect, action, success, "/admin/vocabulary/items");
    }

    private String run(RedirectAttributes redirect, Runnable action, String success, String destination) {
        try { action.run(); redirect.addFlashAttribute("successMessage", success); }
        catch (ContentValidationException ex) { redirect.addFlashAttribute("errorMessage", String.join("; ", ex.getErrors())); }
        return "redirect:" + destination;
    }
}
