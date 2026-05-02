package kz.damulab.lectures;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import kz.damulab.content.ContentGraphService;
import kz.damulab.content.ReferenceOption;
import kz.damulab.content.TopicRepository;

@Controller
public class AdminLecturePageController {
    private static final int ATTACHMENT_FORM_ROWS = 8;
    private static final Pattern ATTACHMENT_FILE_INDEX = Pattern.compile("^attachmentFiles\\[(\\d+)]$");

    private final LectureService lectureService;
    private final ContentGraphService contentGraph;
    private final TopicRepository topics;

    public AdminLecturePageController(
            LectureService lectureService,
            ContentGraphService contentGraph,
            TopicRepository topics
    ) {
        this.lectureService = lectureService;
        this.contentGraph = contentGraph;
        this.topics = topics;
    }

    @GetMapping("/admin/lectures")
    String lectures(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long gradeId,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) LectureStatus status,
            @RequestParam(required = false) String query,
            Model model
    ) {
        Long resolvedSubjectId = resolveSubjectId(subjectId);
        Long resolvedGradeId = resolveGradeId(gradeId);
        List<LectureResponse> items = lectureService.listLectures(topicId, status, query);
        addReferenceModel(model, resolvedSubjectId, resolvedGradeId);
        model.addAttribute("activeAdminNav", "lectures");
        model.addAttribute("lectures", items);
        model.addAttribute("selectedTopicId", topicId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("query", query);
        model.addAttribute("draftCount", items.stream().filter(item -> "draft".equals(item.status())).count());
        model.addAttribute("publishedCount", items.stream().filter(item -> "published".equals(item.status())).count());
        model.addAttribute("withoutControlCount", items.stream().filter(item -> "none".equals(item.controlMode())).count());
        return "admin/lectures";
    }

    @GetMapping("/admin/lectures/new")
    String newLecture(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long gradeId,
            Model model
    ) {
        addReferenceModel(model, resolveSubjectId(subjectId), resolveGradeId(gradeId));
        model.addAttribute("activeAdminNav", "lectures");
        if (!model.containsAttribute("lectureForm")) {
            model.addAttribute("lectureForm", defaultForm());
        }
        model.addAttribute("mode", "create");
        return "admin/lecture-form";
    }

    @PostMapping("/admin/lectures")
    String createLecture(
            @Valid @ModelAttribute("lectureForm") LectureForm form,
            BindingResult bindingResult,
            @RequestParam(defaultValue = "draft") String action,
            @RequestParam(required = false) Long topicIdMirror,
            @RequestParam(required = false) MultiValueMap<String, MultipartFile> multipartFiles,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        applyTopicFallback(form, topicIdMirror);
        ensureAttachmentRows(form);
        if (bindingResult.hasErrors()) {
            addReferenceModel(model, resolveSubjectId(null), resolveGradeId(null));
            model.addAttribute("activeAdminNav", "lectures");
            model.addAttribute("mode", "create");
            return "admin/lecture-form";
        }
        try {
            LectureResponse created = lectureService.createLecture(form, resolveAttachmentFiles(multipartFiles));
            if ("publish".equals(action)) {
                lectureService.publish(created.id());
                redirectAttributes.addFlashAttribute("successMessage", "Р вЂєР ВµР С”РЎвЂ Р С‘РЎРЏ Р С•Р С—РЎС“Р В±Р В»Р С‘Р С”Р С•Р Р†Р В°Р Р…Р В°");
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Р В§Р ВµРЎР‚Р Р…Р С•Р Р†Р С‘Р С” Р В»Р ВµР С”РЎвЂ Р С‘Р С‘ РЎРѓР С•РЎвЂ¦РЎР‚Р В°Р Р…Р ВµР Р…");
            }
            return "redirect:/admin/lectures";
        } catch (LectureException ex) {
            bindingResult.reject("lecture", humanError(ex.getCode()));
            addReferenceModel(model, resolveSubjectId(null), resolveGradeId(null));
            model.addAttribute("activeAdminNav", "lectures");
            model.addAttribute("mode", "create");
            return "admin/lecture-form";
        }
    }

    @GetMapping("/admin/lectures/{id}/edit")
    String editLecture(@PathVariable Long id, Model model) {
        LectureResponse lecture = lectureService.getLecture(id);
        SubjectGradeSelection filters = resolveFiltersForTopic(lecture.topicId());
        addReferenceModel(model, filters.subjectId(), filters.gradeId());
        model.addAttribute("activeAdminNav", "lectures");
        model.addAttribute("lecture", lecture);
        if (!model.containsAttribute("lectureForm")) {
            LectureForm form = lectureService.toEditForm(id);
            ensureAttachmentRows(form);
            model.addAttribute("lectureForm", form);
        } else {
            LectureForm form = (LectureForm) model.getAttribute("lectureForm");
            if (form != null) {
                SubjectGradeSelection formFilters = resolveFiltersForTopic(form.getTopicId());
                addReferenceModel(model, formFilters.subjectId(), formFilters.gradeId());
            }
        }
        model.addAttribute("mode", "edit");
        return "admin/lecture-form";
    }

    @GetMapping("/admin/lectures/{id}/preview")
    String previewLecture(@PathVariable Long id, Model model) {
        model.addAttribute("lecture", lectureService.getLecture(id));
        model.addAttribute("adminPreview", true);
        return "student/lecture";
    }

    @PostMapping("/admin/lectures/{id}")
    String updateLecture(
            @PathVariable Long id,
            @Valid @ModelAttribute("lectureForm") LectureForm form,
            BindingResult bindingResult,
            @RequestParam(defaultValue = "draft") String action,
            @RequestParam(required = false) Long topicIdMirror,
            @RequestParam(required = false) MultiValueMap<String, MultipartFile> multipartFiles,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        applyTopicFallback(form, topicIdMirror);
        ensureAttachmentRows(form);
        if (bindingResult.hasErrors()) {
            SubjectGradeSelection filters = resolveFiltersForTopic(form.getTopicId());
            addReferenceModel(model, filters.subjectId(), filters.gradeId());
            model.addAttribute("activeAdminNav", "lectures");
            model.addAttribute("lecture", lectureService.getLecture(id));
            model.addAttribute("mode", "edit");
            return "admin/lecture-form";
        }
        try {
            lectureService.updateLecture(id, form, resolveAttachmentFiles(multipartFiles));
            if ("publish".equals(action)) {
                lectureService.publish(id);
                redirectAttributes.addFlashAttribute("successMessage", "Р вЂєР ВµР С”РЎвЂ Р С‘РЎРЏ Р С•Р С—РЎС“Р В±Р В»Р С‘Р С”Р С•Р Р†Р В°Р Р…Р В°");
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Р вЂєР ВµР С”РЎвЂ Р С‘РЎРЏ РЎРѓР С•РЎвЂ¦РЎР‚Р В°Р Р…Р ВµР Р…Р В°");
            }
            return "redirect:/admin/lectures";
        } catch (LectureException ex) {
            bindingResult.reject("lecture", humanError(ex.getCode()));
            SubjectGradeSelection filters = resolveFiltersForTopic(form.getTopicId());
            addReferenceModel(model, filters.subjectId(), filters.gradeId());
            model.addAttribute("activeAdminNav", "lectures");
            model.addAttribute("lecture", lectureService.getLecture(id));
            model.addAttribute("mode", "edit");
            return "admin/lecture-form";
        }
    }

    @PostMapping("/admin/lectures/{id}/publish")
    String publish(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            lectureService.publish(id);
            redirectAttributes.addFlashAttribute("successMessage", "Р вЂєР ВµР С”РЎвЂ Р С‘РЎРЏ Р С•Р С—РЎС“Р В±Р В»Р С‘Р С”Р С•Р Р†Р В°Р Р…Р В°");
        } catch (LectureException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/lectures";
    }

    @PostMapping("/admin/lectures/{id}/archive")
    String archive(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            lectureService.archive(id);
            redirectAttributes.addFlashAttribute("successMessage", "Р вЂєР ВµР С”РЎвЂ Р С‘РЎРЏ Р В°РЎР‚РЎвЂ¦Р С‘Р Р†Р С‘РЎР‚Р С•Р Р†Р В°Р Р…Р В°");
        } catch (LectureException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/lectures";
    }

    private void addReferenceModel(Model model, Long subjectId, Long gradeId) {
        model.addAttribute("subjects", contentGraph.listSubjects());
        model.addAttribute("grades", contentGraph.listGrades());
        model.addAttribute("topics", contentGraph.listTopics(subjectId, gradeId));
        model.addAttribute("selectedSubjectId", subjectId);
        model.addAttribute("selectedGradeId", gradeId);
        model.addAttribute("lectureStatuses", LectureStatus.values());
        model.addAttribute("controlModes", LectureControlMode.values());
    }

    private LectureForm defaultForm() {
        LectureForm form = new LectureForm();
        form.setControlMode(LectureControlMode.NONE);
        form.setAutoCheckpointCount(3);
        ensureAttachmentRows(form);
        return form;
    }

    private void ensureAttachmentRows(LectureForm form) {
        while (form.getAttachments().size() < ATTACHMENT_FORM_ROWS) {
            form.getAttachments().add(new LectureAttachmentForm());
        }
    }

    private List<MultipartFile> resolveAttachmentFiles(MultiValueMap<String, MultipartFile> multipartFiles) {
        if (multipartFiles == null || multipartFiles.isEmpty()) {
            return List.of();
        }
        Map<Integer, MultipartFile> indexedFiles = new TreeMap<>();
        int fallbackIndex = 0;
        for (Map.Entry<String, List<MultipartFile>> entry : multipartFiles.entrySet()) {
            Integer indexed = parseAttachmentFileIndex(entry.getKey());
            for (MultipartFile file : entry.getValue()) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                if (indexed != null) {
                    indexedFiles.put(indexed, file);
                    continue;
                }
                while (indexedFiles.containsKey(fallbackIndex)) {
                    fallbackIndex++;
                }
                indexedFiles.put(fallbackIndex, file);
                fallbackIndex++;
            }
        }
        if (indexedFiles.isEmpty()) {
            return List.of();
        }
        int maxIndex = indexedFiles.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        java.util.ArrayList<MultipartFile> ordered = new java.util.ArrayList<>(
                java.util.Collections.nCopies(maxIndex + 1, null));
        indexedFiles.forEach(ordered::set);
        return ordered;
    }

    private Integer parseAttachmentFileIndex(String parameterName) {
        if ("attachmentFiles".equals(parameterName)) {
            return null;
        }
        Matcher matcher = ATTACHMENT_FILE_INDEX.matcher(parameterName == null ? "" : parameterName);
        if (!matcher.matches()) {
            return null;
        }
        return Integer.valueOf(matcher.group(1));
    }

    private SubjectGradeSelection resolveFiltersForTopic(Long topicId) {
        if (topicId != null) {
            SubjectGradeSelection selection = topics.findById(topicId)
                    .map(topic -> new SubjectGradeSelection(topic.getSubject().getId(), topic.getGrade().getId()))
                    .orElse(null);
            if (selection != null) {
                return selection;
            }
        }
        return new SubjectGradeSelection(resolveSubjectId(null), resolveGradeId(null));
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
                .orElseThrow();
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

    private void applyTopicFallback(LectureForm form, Long topicIdMirror) {
        if (form == null || form.getTopicId() != null || topicIdMirror == null) {
            return;
        }
        form.setTopicId(topicIdMirror);
    }

    private String humanError(String code) {
        return switch (code) {
            case "lecture_title_required" -> "Укажите название лекции хотя бы на одном языке.";
            case "lecture_content_required" -> "Добавьте контент лекции хотя бы на одном языке.";
            case "lecture_topic_required" -> "Для публикации выберите тему лекции.";
            case "lecture_bilingual_title_required" -> "Для публикации нужны названия RU и KK.";
            case "lecture_bilingual_content_required" -> "Для публикации заполните контент RU и KK.";
            case "lecture_auto_checkpoint_count_required" -> "Укажите количество вопросов для автоконтроля.";
            case "lecture_auto_checkpoints_not_found" -> "Для автоконтроля нет опубликованных вопросов по теме.";
            case "lecture_manual_checkpoints_required" -> "Для ручного контроля выберите хотя бы один вопрос.";
            case "checkpoint_topic_mismatch" -> "Checkpoint-вопрос должен относиться к теме лекции.";
            case "checkpoint_question_not_published" -> "Checkpoint-вопрос должен быть опубликован.";
            case "lecture_attachment_required" -> "Заполните название и добавьте файл или URL вложения.";
            case "lecture_attachment_url_invalid" -> "Ссылка вложения небезопасна или некорректна.";
            case "lecture_attachment_limit_exceeded" -> "Можно добавить не более 8 вложений.";
            case "lecture_attachment_media_type_invalid" -> "Неподдерживаемый тип вложения.";
            case "lecture_attachment_type_url_mismatch" -> "URL вложения не соответствует выбранному типу.";
            case "lecture_attachment_file_required" -> "Выберите файл вложения.";
            case "lecture_attachment_file_too_large" -> "Файл вложения не должен превышать 25 MB.";
            case "lecture_attachment_storage_unavailable", "lecture_attachment_storage_failed" ->
                    "Хранилище вложений временно недоступно.";
            default -> "Лекция не сохранена: " + code;
        };
    }

    private record SubjectGradeSelection(Long subjectId, Long gradeId) {
    }
}

