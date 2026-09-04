package kz.damulab.content;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Отображает административные страницы управления предметами. */
@Controller
public class AdminSubjectPageController {

    private final SubjectManagementService subjectManagement;

    /** Подключает сервис административных операций с предметами. */
    /** Подключает сервис административных операций с предметами. */
    public AdminSubjectPageController(SubjectManagementService subjectManagement) {
        this.subjectManagement = subjectManagement;
    }

    /** Показывает список всех предметов. */
    @GetMapping("/admin/subjects")
    String subjects(Model model) {
        model.addAttribute("activeAdminNav", "subjects");
        model.addAttribute("subjects", subjectManagement.list());
        return "admin/subjects";
    }

    /** Открывает пустую форму нового предмета. */
    @GetMapping("/admin/subjects/new")
    String newSubject(Model model) {
        addFormModel(model, new SubjectForm(), null, null);
        return "admin/subject-form";
    }

    /** Создаёт предмет из заполненной формы. */
    @PostMapping("/admin/subjects")
    String create(
            @Valid @ModelAttribute("subjectForm") SubjectForm form,
            BindingResult bindingResult,
            @RequestParam(name = "icon", required = false) MultipartFile icon,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            addFormModel(model, form, null, null);
            return "admin/subject-form";
        }
        try {
            subjectManagement.create(form, icon);
            redirectAttributes.addFlashAttribute("successMessage", "Предмет создан");
            return "redirect:/admin/subjects";
        } catch (SubjectManagementException ex) {
            bindingResult.reject("subject", humanError(ex.getCode()));
            addFormModel(model, form, null, null);
            return "admin/subject-form";
        }
    }

    /** Открывает форму существующего предмета. */
    @GetMapping("/admin/subjects/{id}/edit")
    String edit(@PathVariable Long id, Model model) {
        AdminSubjectView subject = subjectManagement.get(id);
        addFormModel(model, subjectManagement.form(id), id, subject.iconUrl());
        return "admin/subject-form";
    }

    /** Сохраняет изменения предмета и его иконки. */
    @PostMapping("/admin/subjects/{id}")
    String update(
            @PathVariable Long id,
            @Valid @ModelAttribute("subjectForm") SubjectForm form,
            BindingResult bindingResult,
            @RequestParam(name = "icon", required = false) MultipartFile icon,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        AdminSubjectView current = subjectManagement.get(id);
        if (bindingResult.hasErrors()) {
            addFormModel(model, form, id, current.iconUrl());
            return "admin/subject-form";
        }
        try {
            subjectManagement.update(id, form, icon);
            redirectAttributes.addFlashAttribute("successMessage", "Предмет обновлён");
            return "redirect:/admin/subjects";
        } catch (SubjectManagementException ex) {
            bindingResult.reject("subject", humanError(ex.getCode()));
            addFormModel(model, form, id, current.iconUrl());
            return "admin/subject-form";
        }
    }

    /** Удаляет предмет, если сервис подтвердил отсутствие связанных тем. */
    @PostMapping("/admin/subjects/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            subjectManagement.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Предмет удалён");
        } catch (SubjectManagementException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/subjects";
    }

    private void addFormModel(Model model, SubjectForm form, Long subjectId, String currentIconUrl) {
        model.addAttribute("activeAdminNav", "subjects");
        model.addAttribute("subjectForm", form);
        model.addAttribute("subjectId", subjectId);
        model.addAttribute("currentIconUrl", currentIconUrl);
        model.addAttribute("editing", subjectId != null);
    }

    private String humanError(String code) {
        return switch (code) {
            case "icon_empty" -> "Выберите непустой PNG-файл";
            case "icon_too_large" -> "Размер иконки не должен превышать 2 МБ";
            case "icon_not_png" -> "Иконка должна быть файлом PNG";
            case "subject_has_topics" -> "Нельзя удалить предмет, у которого есть темы";
            case "subject_in_use" -> "Нельзя удалить предмет, пока он используется в учебных материалах";
            case "subject_not_found" -> "Предмет не найден";
            default -> "Не удалось сохранить предмет";
        };
    }
}
