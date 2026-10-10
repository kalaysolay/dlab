package kz.damulab.quiz.catalog;

import java.util.*;
import kz.damulab.content.GradeRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** SSR-страницы банка утверждений; false передаётся отдельным radio-значением и не теряется. */
@Controller
@RequestMapping("/admin/facts")
public class FactAdminPageController {
    private final FactAdminService facts; private final GradeRepository grades; private final CurrentAdminService currentAdmin;
    public FactAdminPageController(FactAdminService facts, GradeRepository grades, CurrentAdminService currentAdmin) { this.facts = facts; this.grades = grades; this.currentAdmin = currentAdmin; }

    /** Показывает наборы и утверждения без изменения состояния. */
    @GetMapping
    public String list(@RequestParam(required = false) Long setId, @RequestParam(required = false) EditorialStatus status,
                       @RequestParam(required = false) Long gradeId, @RequestParam(defaultValue = "") String query, Model model) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        List<FactStatement> filtered = facts.listStatements().stream().filter(s -> setId == null || s.getSet().getId().equals(setId))
                .filter(s -> status == null || s.getStatus() == status || (s.getDraftVersion() != null && s.getDraftVersion().getStatus() == status))
                .filter(s -> gradeId == null || shown(s) != null && (shown(s).isUniversalAudience() || shown(s).getGradeIds().contains(gradeId)))
                .filter(s -> q.isBlank() || Optional.ofNullable(shown(s)).map(v -> v.getTextRu().toLowerCase(Locale.ROOT).contains(q) || v.getTextKk().toLowerCase(Locale.ROOT).contains(q)).orElse(false)).toList();
        model.addAttribute("activeAdminNav", "facts"); model.addAttribute("sets", facts.listSets()); model.addAttribute("statements", filtered);
        model.addAttribute("selectedSetId", setId); model.addAttribute("selectedStatus", status); model.addAttribute("query", query);
        model.addAttribute("selectedGradeId", gradeId); model.addAttribute("grades", grades.findAllByOrderByGradeNoAsc());
        model.addAttribute("statuses", EditorialStatus.values()); model.addAttribute("setForm", new FactSetForm()); return "admin/fact-bank";
    }

    @GetMapping("/statements/new") public String newStatement(@RequestParam(required = false) Long setId, Model model) { FactStatementForm f = new FactStatementForm(); f.setSetId(setId); return form(model, f, null); }
    @GetMapping("/statements/{id}/edit") public String edit(@PathVariable Long id, Model model) { return form(model, facts.form(id), id); }

    @PostMapping("/sets")
    public String createSet(@ModelAttribute FactSetForm form, Authentication auth, RedirectAttributes redirect) { return run(redirect, () -> facts.createSet(form, currentAdmin.requireId(auth)), "Набор утверждений создан"); }

    @PostMapping("/sets/{id}/{action}")
    public String setAction(@PathVariable Long id, @PathVariable String action, Authentication auth, RedirectAttributes redirect) {
        return run(redirect, () -> { Long actor = currentAdmin.requireId(auth); switch (action) {
            case "submit" -> facts.transitionSet(id, EditorialStatus.NEEDS_REVIEW, actor); case "approve" -> facts.transitionSet(id, EditorialStatus.APPROVED, actor);
            case "publish" -> facts.transitionSet(id, EditorialStatus.PUBLISHED, actor); case "archive" -> facts.archiveSet(id, actor);
            default -> throw new ContentValidationException("Неизвестное действие"); } }, "Статус набора обновлён");
    }

    @PostMapping("/statements")
    public String create(@ModelAttribute("statementForm") FactStatementForm form, Authentication auth, Model model, RedirectAttributes redirect) {
        try { FactStatement value = facts.createStatement(form, currentAdmin.requireId(auth)); redirect.addFlashAttribute("successMessage", "Черновик утверждения создан"); return "redirect:/admin/facts/statements/" + value.getId() + "/edit"; }
        catch (ContentValidationException ex) { model.addAttribute("formErrors", ex.getErrors()); return form(model, form, null); }
    }

    @PostMapping("/statements/{id}")
    public String save(@PathVariable Long id, @ModelAttribute("statementForm") FactStatementForm form, Authentication auth, Model model, RedirectAttributes redirect) {
        try { facts.saveDraft(id, form, currentAdmin.requireId(auth)); redirect.addFlashAttribute("successMessage", "Новая редакция утверждения сохранена"); return "redirect:/admin/facts/statements/" + id + "/edit"; }
        catch (ContentValidationException ex) { model.addAttribute("formErrors", ex.getErrors()); return form(model, form, id); }
    }

    @PostMapping("/statements/{id}/{action}")
    public String action(@PathVariable Long id, @PathVariable String action, Authentication auth, RedirectAttributes redirect) {
        return run(redirect, () -> { Long actor = currentAdmin.requireId(auth); switch (action) {
            case "submit" -> facts.submit(id, actor); case "approve" -> facts.approve(id, actor); case "publish" -> facts.publish(id, actor); case "archive" -> facts.archiveStatement(id, actor);
            default -> throw new ContentValidationException("Неизвестное действие"); } }, "Статус утверждения обновлён");
    }

    private String form(Model model, FactStatementForm form, Long id) {
        model.addAttribute("activeAdminNav", "facts"); model.addAttribute("statementForm", form); model.addAttribute("statementId", id);
        model.addAttribute("editing", id != null); model.addAttribute("sets", facts.listSets()); model.addAttribute("grades", grades.findAllByOrderByGradeNoAsc());
        if (id != null) model.addAttribute("statement", facts.requireStatement(id)); return "admin/fact-form";
    }
    private FactStatementVersion shown(FactStatement s) { return s.getDraftVersion() != null ? s.getDraftVersion() : s.getPublishedVersion(); }
    private String run(RedirectAttributes redirect, Runnable action, String success) { try { action.run(); redirect.addFlashAttribute("successMessage", success); } catch (ContentValidationException ex) { redirect.addFlashAttribute("errorMessage", String.join("; ", ex.getErrors())); } return "redirect:/admin/facts"; }
}
