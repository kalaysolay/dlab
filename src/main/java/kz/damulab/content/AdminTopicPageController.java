package kz.damulab.content;

import java.util.List;

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

@Controller
public class AdminTopicPageController {

    private final ContentGraphService contentGraph;

    public AdminTopicPageController(ContentGraphService contentGraph) {
        this.contentGraph = contentGraph;
    }

    @GetMapping("/admin/topics")
    String topics(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long gradeId,
            Model model
    ) {
        addTopicModel(model, subjectId, gradeId, new TopicForm());
        return "admin/topics";
    }

    @GetMapping("/admin/topics/tree")
    String topicTree(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long gradeId,
            @RequestParam(required = false) Long topicId,
            Model model
    ) {
        Long resolvedSubjectId = resolveSubjectId(subjectId);
        Long resolvedGradeId = resolveGradeId(gradeId);
        model.addAttribute("activeAdminNav", "topics");
        model.addAttribute("subjects", contentGraph.listSubjects());
        model.addAttribute("grades", contentGraph.listGrades());
        model.addAttribute("selectedSubjectId", resolvedSubjectId);
        model.addAttribute("selectedGradeId", resolvedGradeId);
        model.addAttribute("tree", contentGraph.topicTree(resolvedSubjectId, resolvedGradeId));
        model.addAttribute("topics", contentGraph.listTopics(resolvedSubjectId, resolvedGradeId));
        if (!model.containsAttribute("topicForm")) {
            TopicForm form = topicId == null ? new TopicForm() : toForm(contentGraph.getTopic(topicId));
            form.setSubjectId(resolvedSubjectId);
            form.setGradeId(resolvedGradeId);
            model.addAttribute("topicForm", form);
        }
        if (!model.containsAttribute("skillForm")) {
            AtomicSkillForm skillForm = new AtomicSkillForm();
            skillForm.setTopicId(topicId);
            model.addAttribute("skillForm", skillForm);
        }
        if (topicId != null) {
            model.addAttribute("selectedTopic", contentGraph.getTopic(topicId));
            model.addAttribute("selectedSkills", contentGraph.listSkills(topicId));
        } else {
            model.addAttribute("selectedSkills", List.of());
        }
        return "admin/topic-tree";
    }

    @PostMapping("/admin/topics")
    String createTopic(
            @Valid @ModelAttribute("topicForm") TopicForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            addTopicModel(model, form.getSubjectId(), form.getGradeId(), form);
            return "admin/topics";
        }
        try {
            TopicResponse created = contentGraph.createTopic(form);
            redirectAttributes.addFlashAttribute("successMessage", "Тема создана");
            return redirectToTopics(created.subjectId(), created.gradeId());
        } catch (ContentGraphException ex) {
            bindingResult.reject("topic", humanError(ex.getCode()));
            addTopicModel(model, form.getSubjectId(), form.getGradeId(), form);
            return "admin/topics";
        }
    }

    @PostMapping("/admin/topics/{id}")
    String updateTopic(
            @PathVariable Long id,
            @Valid @ModelAttribute("topicForm") TopicForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Проверьте обязательные поля темы");
            return redirectToTree(form.getSubjectId(), form.getGradeId(), id);
        }
        try {
            TopicResponse updated = contentGraph.updateTopic(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "Тема обновлена");
            return redirectToTree(updated.subjectId(), updated.gradeId(), updated.id());
        } catch (ContentGraphException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
            return redirectToTree(form.getSubjectId(), form.getGradeId(), id);
        }
    }

    @PostMapping("/admin/topics/{id}/delete")
    String deleteTopic(
            @PathVariable Long id,
            @RequestParam Long subjectId,
            @RequestParam Long gradeId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            contentGraph.deleteTopic(id);
            redirectAttributes.addFlashAttribute("successMessage", "Тема удалена (скрыта из списков)");
        } catch (ContentGraphException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return redirectToTopics(subjectId, gradeId);
    }

    @PostMapping("/admin/topics/{id}/restore")
    String restoreTopic(
            @PathVariable Long id,
            @RequestParam Long subjectId,
            @RequestParam Long gradeId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            contentGraph.restoreTopic(id);
            redirectAttributes.addFlashAttribute("successMessage", "Тема восстановлена");
        } catch (ContentGraphException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return redirectToTopics(subjectId, gradeId);
    }

    @PostMapping("/admin/topics/{topicId}/skills")
    String createSkill(
            @PathVariable Long topicId,
            @Valid @ModelAttribute("skillForm") AtomicSkillForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        TopicResponse topic = contentGraph.getTopic(topicId);
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Проверьте обязательные поля навыка");
            return redirectToTree(topic.subjectId(), topic.gradeId(), topic.id());
        }
        try {
            contentGraph.createSkill(topicId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Атомарный навык создан");
        } catch (ContentGraphException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return redirectToTree(topic.subjectId(), topic.gradeId(), topic.id());
    }

    @PostMapping("/admin/skills/{skillId}")
    String updateSkill(
            @PathVariable Long skillId,
            @Valid @ModelAttribute AtomicSkillForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        TopicResponse topic = contentGraph.getTopic(form.getTopicId());
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Проверьте обязательные поля навыка");
            return redirectToTree(topic.subjectId(), topic.gradeId(), topic.id());
        }
        try {
            contentGraph.updateSkill(skillId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Атомарный навык обновлен");
        } catch (ContentGraphException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return redirectToTree(topic.subjectId(), topic.gradeId(), topic.id());
    }

    @PostMapping("/admin/skills/{skillId}/delete")
    String deleteSkill(
            @PathVariable Long skillId,
            @RequestParam Long topicId,
            RedirectAttributes redirectAttributes
    ) {
        TopicResponse topic = contentGraph.getTopic(topicId);
        try {
            contentGraph.deleteSkill(skillId);
            redirectAttributes.addFlashAttribute("successMessage", "Атомарный навык удален");
        } catch (ContentGraphException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return redirectToTree(topic.subjectId(), topic.gradeId(), topic.id());
    }

    private void addTopicModel(Model model, Long subjectId, Long gradeId, TopicForm form) {
        Long resolvedSubjectId = resolveSubjectId(subjectId);
        Long resolvedGradeId = resolveGradeId(gradeId);
        if (form.getSubjectId() == null) {
            form.setSubjectId(resolvedSubjectId);
        }
        if (form.getGradeId() == null) {
            form.setGradeId(resolvedGradeId);
        }
        List<TopicResponse> topics = contentGraph.listTopics(resolvedSubjectId, resolvedGradeId);
        model.addAttribute("activeAdminNav", "topics");
        model.addAttribute("subjects", contentGraph.listSubjects());
        model.addAttribute("grades", contentGraph.listGrades());
        model.addAttribute("topics", topics);
        model.addAttribute("selectedSubjectId", resolvedSubjectId);
        model.addAttribute("selectedGradeId", resolvedGradeId);
        model.addAttribute("topicCount", topics.size());
        model.addAttribute("rootCount", topics.stream().filter(topic -> topic.parentId() == null).count());
        model.addAttribute("blockedDeleteCount", topics.stream()
                .filter(topic -> topic.childCount() > 0 || topic.skillCount() > 0)
                .count());
        if (!model.containsAttribute("topicForm")) {
            model.addAttribute("topicForm", form);
        }
    }

    private Long resolveSubjectId(Long subjectId) {
        if (subjectId != null) {
            return subjectId;
        }
        return contentGraph.listSubjects().stream()
                .filter(subject -> "math".equals(subject.code()))
                .findFirst()
                .or(() -> contentGraph.listSubjects().stream().findFirst())
                .map(ReferenceOption::id)
                .orElseThrow(() -> new ContentGraphException("subject_not_found"));
    }

    private Long resolveGradeId(Long gradeId) {
        if (gradeId != null) {
            return gradeId;
        }
        return contentGraph.listGrades().stream()
                .filter(grade -> Integer.valueOf(4).equals(grade.gradeNo()))
                .findFirst()
                .or(() -> contentGraph.listGrades().stream().findFirst())
                .map(GradeOption::id)
                .orElseThrow(() -> new ContentGraphException("grade_not_found"));
    }

    private TopicForm toForm(TopicResponse topic) {
        TopicForm form = new TopicForm();
        form.setSubjectId(topic.subjectId());
        form.setGradeId(topic.gradeId());
        form.setParentId(topic.parentId());
        form.setCode(topic.code());
        form.setTitleRu(topic.titleRu());
        form.setTitleKk(topic.titleKk());
        return form;
    }

    private String redirectToTopics(Long subjectId, Long gradeId) {
        return "redirect:/admin/topics?subjectId=" + subjectId + "&gradeId=" + gradeId;
    }

    private String redirectToTree(Long subjectId, Long gradeId, Long topicId) {
        return "redirect:/admin/topics/tree?subjectId=" + subjectId + "&gradeId=" + gradeId + "&topicId=" + topicId;
    }

    private String humanError(String code) {
        return switch (code) {
            case "topic_duplicate" -> "Тема с таким кодом или названием уже есть в этой ветке";
            case "topic_parent_deleted" -> "Сначала восстановите родительскую тему";
            case "topic_parent_scope_mismatch" -> "Родительская тема должна быть в том же предмете и классе";
            case "topic_parent_cycle" -> "Тему нельзя сделать дочерней для самой себя или своего потомка";
            case "skill_duplicate" -> "Навык с таким кодом или названием уже есть в этой теме";
            case "skill_topic_mismatch" -> "Навык должен сохраняться внутри выбранной темы";
            case "skill_has_questions" -> "Нельзя удалить навык, пока к нему привязаны вопросы";
            default -> "Изменение темы не сохранено: " + code;
        };
    }
}
