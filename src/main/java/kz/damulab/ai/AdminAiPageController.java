package kz.damulab.ai;

import jakarta.validation.Valid;
import kz.damulab.content.ContentGraphService;
import kz.damulab.questions.QuestionType;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminAiPageController {

    private final AiContentFactoryService aiContentFactory;
    private final ContentGraphService contentGraph;

    public AdminAiPageController(AiContentFactoryService aiContentFactory, ContentGraphService contentGraph) {
        this.aiContentFactory = aiContentFactory;
        this.contentGraph = contentGraph;
    }

    @GetMapping("/admin/questions/ai-generate")
    String aiGeneratePage(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long gradeId,
            @RequestParam(required = false) Long jobId,
            Model model
    ) {
        Long resolvedSubjectId = resolveSubjectId(subjectId);
        Long resolvedGradeId = resolveGradeId(gradeId);
        addReferenceModel(model, resolvedSubjectId, resolvedGradeId);
        if (!model.containsAttribute("aiForm")) {
            AiQuestionGenerationForm form = new AiQuestionGenerationForm();
            contentGraph.listTopics(resolvedSubjectId, resolvedGradeId).stream()
                    .findFirst()
                    .ifPresent(topic -> form.setTopicId(topic.id()));
            model.addAttribute("aiForm", form);
        }
        if (jobId != null) {
            model.addAttribute("job", aiContentFactory.getJob(jobId));
        }
        model.addAttribute("editForm", new AiGeneratedQuestionItemEditForm());
        return "admin/question-ai-generate";
    }

    @PostMapping("/admin/questions/ai-generate")
    String generate(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long gradeId,
            @Valid @ModelAttribute("aiForm") AiQuestionGenerationForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            addReferenceModel(model, resolveSubjectId(subjectId), resolveGradeId(gradeId));
            model.addAttribute("editForm", new AiGeneratedQuestionItemEditForm());
            return "admin/question-ai-generate";
        }
        try {
            AiGenerationJobResponse job = aiContentFactory.createQuestionGenerationJob(form);
            redirectAttributes.addFlashAttribute("successMessage", "AI job создан");
            return "redirect:/admin/questions/ai-generate?jobId=" + job.id();
        } catch (AiContentFactoryException ex) {
            bindingResult.reject("ai", humanError(ex.getCode()));
            addReferenceModel(model, resolveSubjectId(subjectId), resolveGradeId(gradeId));
            model.addAttribute("editForm", new AiGeneratedQuestionItemEditForm());
            return "admin/question-ai-generate";
        }
    }

    @PostMapping("/admin/ai/jobs/{jobId}/retry")
    String retry(@PathVariable Long jobId, RedirectAttributes redirectAttributes) {
        try {
            aiContentFactory.retry(jobId);
            redirectAttributes.addFlashAttribute("successMessage", "Повторный запуск выполнен");
        } catch (AiContentFactoryException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/questions/ai-generate?jobId=" + jobId;
    }

    @PostMapping("/admin/ai/batches/{batchId}/items/{itemId}/approve")
    String approve(
            @PathVariable Long batchId,
            @PathVariable Long itemId,
            @RequestParam Long jobId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            aiContentFactory.approveItem(batchId, itemId);
            redirectAttributes.addFlashAttribute("successMessage", "AI-вопрос перенесен в банк как needs_review");
        } catch (AiContentFactoryException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/questions/ai-generate?jobId=" + jobId;
    }

    @PostMapping("/admin/ai/batches/{batchId}/items/{itemId}/edit")
    String edit(
            @PathVariable Long batchId,
            @PathVariable Long itemId,
            @RequestParam Long jobId,
            @Valid @ModelAttribute("editForm") AiGeneratedQuestionItemEditForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Заполните RU, KK и источник перед сохранением правки");
            return "redirect:/admin/questions/ai-generate?jobId=" + jobId;
        }
        try {
            aiContentFactory.editItem(batchId, itemId, form);
            redirectAttributes.addFlashAttribute("successMessage", "AI-черновик обновлен");
        } catch (AiContentFactoryException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/questions/ai-generate?jobId=" + jobId;
    }

    @PostMapping("/admin/ai/batches/{batchId}/items/{itemId}/delete")
    String delete(
            @PathVariable Long batchId,
            @PathVariable Long itemId,
            @RequestParam Long jobId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            aiContentFactory.deleteItem(batchId, itemId);
            redirectAttributes.addFlashAttribute("successMessage", "AI-черновик удален из review batch");
        } catch (AiContentFactoryException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/questions/ai-generate?jobId=" + jobId;
    }

    private void addReferenceModel(Model model, Long subjectId, Long gradeId) {
        model.addAttribute("activeAdminNav", "question-ai");
        model.addAttribute("subjects", contentGraph.listSubjects());
        model.addAttribute("grades", contentGraph.listGrades());
        model.addAttribute("topics", contentGraph.listTopics(subjectId, gradeId));
        model.addAttribute("selectedSubjectId", subjectId);
        model.addAttribute("selectedGradeId", gradeId);
        model.addAttribute("questionTypes", QuestionType.values());
        model.addAttribute("languageModes", AiLanguageMode.values());
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
            case "stub_provider_failure" -> "StubAiProvider вернул тестовый сбой";
            case "ai_provider_disabled" -> "Real AI providers выключены feature flag; используйте stub или включите server-side config";
            case "openai_api_key_missing" -> "Не задан server-side OPENAI_API_KEY";
            case "deepseek_api_key_missing" -> "Не задан server-side DEEPSEEK_API_KEY";
            case "openai_request_failed" -> "OpenAI adapter вернул ошибку";
            case "deepseek_request_failed" -> "DeepSeek adapter вернул ошибку";
            case "ai_schema_invalid" -> "AI output не прошел schema validation";
            case "ai_job_retry_requires_failed_status" -> "Повтор доступен только для failed job";
            case "ai_item_not_approvable" -> "Этот AI-черновик нельзя одобрить";
            case "ai_item_not_editable" -> "Этот AI-черновик нельзя редактировать";
            case "ai_item_approved_not_deletable" -> "Одобренный AI-черновик уже перенесен в банк вопросов";
            case "skill_topic_mismatch" -> "Атомарный навык должен принадлежать выбранной теме";
            default -> "AI Content Factory: " + code;
        };
    }
}
