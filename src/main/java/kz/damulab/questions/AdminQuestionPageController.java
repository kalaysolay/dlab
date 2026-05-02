package kz.damulab.questions;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import kz.damulab.content.ContentGraphService;

@Controller
public class AdminQuestionPageController {

    private final QuestionBankService questionBank;
    private final ContentGraphService contentGraph;

    public AdminQuestionPageController(QuestionBankService questionBank, ContentGraphService contentGraph) {
        this.questionBank = questionBank;
        this.contentGraph = contentGraph;
    }

    @GetMapping("/admin/questions")
    String questions(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long gradeId,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) QuestionStatus status,
            @RequestParam(required = false) QuestionType type,
            @RequestParam(required = false) QuestionQualityFilter quality,
            @RequestParam(required = false) String query,
            Model model
    ) {
        Long resolvedSubjectId = resolveSubjectId(subjectId);
        Long resolvedGradeId = resolveGradeId(gradeId);
        List<QuestionResponse> items = questionBank.listQuestions(topicId, status, type, query);
        QuestionHealthSummaryResponse health = questionBank.listQuestionHealth(quality);
        if (quality != null) {
            Set<Long> healthyQuestionIds = health.items().stream()
                    .map(QuestionHealthItemResponse::questionId)
                    .collect(Collectors.toSet());
            items = items.stream()
                    .filter(item -> healthyQuestionIds.contains(item.id()))
                    .toList();
        }
        addReferenceModel(model, resolvedSubjectId, resolvedGradeId);
        model.addAttribute("activeAdminNav", "questions");
        model.addAttribute("questions", items);
        model.addAttribute("health", health);
        model.addAttribute("healthByQuestionId", health.items().stream()
                .collect(Collectors.toMap(QuestionHealthItemResponse::questionId, item -> item)));
        model.addAttribute("selectedTopicId", topicId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedType", type);
        model.addAttribute("selectedQuality", quality);
        model.addAttribute("query", query);
        model.addAttribute("draftCount", items.stream().filter(item -> "draft".equals(item.status())).count());
        model.addAttribute("reviewCount", items.stream().filter(item -> "needs_review".equals(item.status())).count());
        model.addAttribute("publishedCount", items.stream().filter(item -> "published".equals(item.status())).count());
        return "admin/questions";
    }

    @GetMapping("/admin/questions/new")
    String newQuestion(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long gradeId,
            Model model
    ) {
        Long resolvedSubjectId = resolveSubjectId(subjectId);
        Long resolvedGradeId = resolveGradeId(gradeId);
        addReferenceModel(model, resolvedSubjectId, resolvedGradeId);
        model.addAttribute("activeAdminNav", "questions");
        model.addAttribute("mode", "create");
        if (!model.containsAttribute("questionForm")) {
            model.addAttribute("questionForm", defaultForm());
        }
        return "admin/question-form";
    }

    @GetMapping("/admin/questions/{id}/edit")
    String editQuestion(@PathVariable Long id, Model model) {
        QuestionEditView edit = questionBank.getQuestionEditView(id);
        addReferenceModel(model, edit.subjectId(), edit.gradeId());
        model.addAttribute("activeAdminNav", "questions");
        model.addAttribute("mode", "edit");
        model.addAttribute("questionId", edit.questionId());
        model.addAttribute("questionVersionNo", edit.versionNo());
        model.addAttribute("questionCurrentStatus", edit.status());
        if (!model.containsAttribute("questionForm")) {
            model.addAttribute("questionForm", edit.form());
        }
        return "admin/question-form";
    }

    @GetMapping("/admin/questions/health")
    String health(@RequestParam(required = false) QuestionQualityFilter quality, Model model) {
        model.addAttribute("activeAdminNav", "question-health");
        model.addAttribute("health", questionBank.listQuestionHealth(quality));
        model.addAttribute("selectedQuality", quality);
        model.addAttribute("qualityFilters", QuestionQualityFilter.values());
        return "admin/question-health";
    }

    @PostMapping("/admin/questions")
    String createQuestion(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long gradeId,
            @Valid @ModelAttribute("questionForm") QuestionForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            addReferenceModel(model, resolveSubjectId(subjectId), resolveGradeId(gradeId));
            model.addAttribute("activeAdminNav", "questions");
            model.addAttribute("mode", "create");
            return "admin/question-form";
        }
        try {
            QuestionResponse created = questionBank.createQuestion(form);
            redirectAttributes.addFlashAttribute("successMessage", "Вопрос создан");
            return "redirect:/admin/questions?topicId=" + created.topicId();
        } catch (QuestionBankException ex) {
            bindingResult.reject("question", humanError(ex.getCode()));
            addReferenceModel(model, resolveSubjectId(subjectId), resolveGradeId(gradeId));
            model.addAttribute("activeAdminNav", "questions");
            model.addAttribute("mode", "create");
            return "admin/question-form";
        }
    }

    @PostMapping("/admin/questions/{id}")
    String updateQuestion(
            @PathVariable Long id,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long gradeId,
            @Valid @ModelAttribute("questionForm") QuestionForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        QuestionEditView existing = questionBank.getQuestionEditView(id);
        Long resolvedSubjectId = resolveSubjectId(subjectId != null ? subjectId : existing.subjectId());
        Long resolvedGradeId = resolveGradeId(gradeId != null ? gradeId : existing.gradeId());
        if (bindingResult.hasErrors()) {
            addReferenceModel(model, resolvedSubjectId, resolvedGradeId);
            model.addAttribute("activeAdminNav", "questions");
            model.addAttribute("mode", "edit");
            model.addAttribute("questionId", existing.questionId());
            model.addAttribute("questionVersionNo", existing.versionNo());
            model.addAttribute("questionCurrentStatus", existing.status());
            return "admin/question-form";
        }
        try {
            questionBank.updateQuestion(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "Вопрос обновлен");
            return "redirect:/admin/questions";
        } catch (QuestionBankException ex) {
            bindingResult.reject("question", humanError(ex.getCode()));
            addReferenceModel(model, resolvedSubjectId, resolvedGradeId);
            model.addAttribute("activeAdminNav", "questions");
            model.addAttribute("mode", "edit");
            model.addAttribute("questionId", existing.questionId());
            model.addAttribute("questionVersionNo", existing.versionNo());
            model.addAttribute("questionCurrentStatus", existing.status());
            return "admin/question-form";
        }
    }

    @PostMapping("/admin/questions/{id}/approve")
    String approve(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            questionBank.approve(id);
            redirectAttributes.addFlashAttribute("successMessage", "Вопрос одобрен");
        } catch (QuestionBankException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/questions";
    }

    @PostMapping("/admin/questions/{id}/publish")
    String publish(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            questionBank.publish(id);
            redirectAttributes.addFlashAttribute("successMessage", "Вопрос опубликован");
        } catch (QuestionBankException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/questions";
    }

    @PostMapping("/admin/questions/{id}/archive")
    String archive(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            questionBank.archive(id);
            redirectAttributes.addFlashAttribute("successMessage", "Вопрос архивирован");
        } catch (QuestionBankException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/questions";
    }

    @PostMapping("/admin/questions/{id}/flag")
    String flag(@PathVariable Long id, @RequestParam(required = false) String reason, RedirectAttributes redirectAttributes) {
        try {
            questionBank.flagForReview(id, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Вопрос отправлен на review");
        } catch (QuestionBankException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/questions";
    }

    @PostMapping("/admin/questions/{id}/flags")
    String createFlag(
            @PathVariable Long id,
            @RequestParam QuestionFlagSource source,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes
    ) {
        try {
            questionBank.createQuestionFlag(id, source, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Флаг качества добавлен");
        } catch (QuestionBankException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/questions/health";
    }

    private void addReferenceModel(Model model, Long subjectId, Long gradeId) {
        model.addAttribute("subjects", contentGraph.listSubjects());
        model.addAttribute("grades", contentGraph.listGrades());
        model.addAttribute("topics", contentGraph.listTopics(subjectId, gradeId));
        model.addAttribute("selectedSubjectId", subjectId);
        model.addAttribute("selectedGradeId", gradeId);
        model.addAttribute("questionTypes", QuestionType.values());
        model.addAttribute("questionStatuses", QuestionStatus.values());
        model.addAttribute("qualityFilters", QuestionQualityFilter.values());
        model.addAttribute("fillModes", FillMatchMode.values());
    }

    private QuestionForm defaultForm() {
        QuestionForm form = new QuestionForm();
        form.setStatus(QuestionStatus.DRAFT);
        form.setType(QuestionType.SCQ);
        form.setOptions(new java.util.ArrayList<>(List.of(
                new ChoiceOptionForm("A", "", "", false),
                new ChoiceOptionForm("B", "", "", true),
                new ChoiceOptionForm("C", "", "", false),
                new ChoiceOptionForm("D", "", "", false)
        )));
        form.setMatchingPairs(new java.util.ArrayList<>(List.of(
                new MatchingPairForm("", "", "", ""),
                new MatchingPairForm("", "", "", "")
        )));
        form.setFillAnswers(new java.util.ArrayList<>(List.of(
                new FillAnswerForm("", "", FillMatchMode.EXACT, null)
        )));
        return form;
    }

    private Long resolveSubjectId(Long subjectId) {
        if (subjectId != null) {
            return subjectId;
        }
        return contentGraph.listSubjects().stream().findFirst().orElseThrow().id();
    }

    private Long resolveGradeId(Long gradeId) {
        if (gradeId != null) {
            return gradeId;
        }
        return contentGraph.listGrades().stream()
                .filter(grade -> Integer.valueOf(4).equals(grade.gradeNo()))
                .findFirst()
                .or(() -> contentGraph.listGrades().stream().findFirst())
                .orElseThrow()
                .id();
    }

    private String humanError(String code) {
        return switch (code) {
            case "scq_requires_exactly_one_correct" -> "Для SCQ нужен ровно один правильный ответ";
            case "mcq_requires_one_correct" -> "Для MCQ нужен минимум один правильный ответ";
            case "choice_requires_two_options" -> "Добавьте минимум два варианта ответа";
            case "choice_option_text_required" -> "Заполните RU и KK текст каждого варианта";
            case "matching_requires_two_pairs" -> "Для сопоставления нужны минимум две пары";
            case "matching_pair_required" -> "Заполните обе стороны пары RU и KK";
            case "fill_in_requires_answer" -> "Для FILL_IN нужен ключ ответа";
            case "fill_in_answer_required" -> "Заполните placeholder, ответ и правило проверки";
            case "fill_in_tolerance_required" -> "Для числового допуска укажите неотрицательный tolerance";
            case "question_correct_answer_required" -> "Сначала задайте корректный правильный ответ";
            case "skill_topic_mismatch" -> "Атомарный навык должен принадлежать выбранной теме";
            case "question_source_required" -> "Источник вопроса обязателен";
            case "question_flag_reason_required" -> "Укажите причину флага";
            case "question_not_approved" -> "Сначала одобрите вопрос";
            default -> "Вопрос не сохранен: " + code;
        };
    }
}
