package kz.damulab.content;

import java.util.ArrayList;
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

import kz.damulab.questions.ChoiceOptionForm;
import kz.damulab.questions.FillAnswerForm;
import kz.damulab.questions.FillMatchMode;
import kz.damulab.questions.MatchingPairForm;
import kz.damulab.questions.QuestionType;

/**
 * Страницы админки для эталонов вопросов темы (вариант B — few-shot для AI-генерации).
 *
 * <p>Экраны (макеты — docs/topic-ai-examples):
 * <ul>
 *   <li>{@code GET /admin/ai-examples} — хаб: покрытие тем эталонами (пункт меню «Эталоны для ИИ»);</li>
 *   <li>{@code GET /admin/topics/{topicId}/ai-examples} — список эталонов темы;</li>
 *   <li>{@code GET .../ai-examples/new} и {@code /{exampleId}} — редактор (создание/правка);</li>
 *   <li>{@code POST .../ai-examples}, {@code /{exampleId}}, {@code /{exampleId}/delete} — CRUD.</li>
 * </ul>
 *
 * <p>Стиль повторяет {@link AdminTopicPageController}: server-rendered Thymeleaf, редиректы с
 * flash-сообщениями, бизнес-ошибки {@link TopicAiExampleException} превращаются в человекочитаемый
 * текст в {@link #humanError}.
 */
@Controller
public class AdminTopicAiExamplePageController {

    private static final String NAV = "ai-examples";

    private final TopicAiExampleService exampleService;
    private final ContentGraphService contentGraph;

    public AdminTopicAiExamplePageController(TopicAiExampleService exampleService, ContentGraphService contentGraph) {
        this.exampleService = exampleService;
        this.contentGraph = contentGraph;
    }

    /** Хаб «Эталоны для ИИ»: таблица покрытия тем по выбранной паре предмет+класс. */
    @GetMapping("/admin/ai-examples")
    String hub(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long gradeId,
            Model model
    ) {
        Long resolvedSubjectId = resolveSubjectId(subjectId);
        Long resolvedGradeId = resolveGradeId(gradeId);
        List<TopicAiExampleCoverageResponse> coverage = exampleService.coverage(resolvedSubjectId, resolvedGradeId);
        model.addAttribute("activeAdminNav", NAV);
        model.addAttribute("subjects", contentGraph.listSubjects());
        model.addAttribute("grades", contentGraph.listGrades());
        model.addAttribute("selectedSubjectId", resolvedSubjectId);
        model.addAttribute("selectedGradeId", resolvedGradeId);
        model.addAttribute("coverage", coverage);
        // Сводка для карточек-метрик хаба.
        model.addAttribute("topicsTotal", coverage.size());
        model.addAttribute("topicsWithExamples", coverage.stream().filter(c -> c.total() > 0).count());
        model.addAttribute("topicsWithoutExamples", coverage.stream().filter(c -> c.total() == 0).count());
        return "admin/ai-examples";
    }

    /** Список эталонов конкретной темы. */
    @GetMapping("/admin/topics/{topicId}/ai-examples")
    String list(@PathVariable Long topicId, Model model) {
        TopicResponse topic = contentGraph.getTopic(topicId);
        List<TopicAiExampleResponse> examples = exampleService.listByTopic(topicId);
        model.addAttribute("activeAdminNav", NAV);
        model.addAttribute("topic", topic);
        model.addAttribute("examples", examples);
        model.addAttribute("exampleCount", examples.size());
        model.addAttribute("exampleLimit", TopicAiExampleService.MAX_EXAMPLES_PER_TOPIC);
        model.addAttribute("limitReached", examples.size() >= TopicAiExampleService.MAX_EXAMPLES_PER_TOPIC);
        return "admin/topic-ai-examples";
    }

    /** Редактор нового эталона: форма с преднастроенными пустыми строками ключа. */
    @GetMapping("/admin/topics/{topicId}/ai-examples/new")
    String createForm(@PathVariable Long topicId, Model model) {
        if (!model.containsAttribute("exampleForm")) {
            model.addAttribute("exampleForm", blankForm());
        }
        prepareEditor(model, topicId, null);
        return "admin/topic-ai-example-form";
    }

    /** Редактор существующего эталона. */
    @GetMapping("/admin/topics/{topicId}/ai-examples/{exampleId}")
    String editForm(@PathVariable Long topicId, @PathVariable Long exampleId, Model model) {
        if (!model.containsAttribute("exampleForm")) {
            model.addAttribute("exampleForm", toForm(exampleService.get(topicId, exampleId)));
        }
        prepareEditor(model, topicId, exampleId);
        return "admin/topic-ai-example-form";
    }

    @PostMapping("/admin/topics/{topicId}/ai-examples")
    String create(
            @PathVariable Long topicId,
            @Valid @ModelAttribute("exampleForm") TopicAiExampleForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            prepareEditor(model, topicId, null);
            return "admin/topic-ai-example-form";
        }
        try {
            exampleService.create(topicId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Эталон добавлен");
            return "redirect:/admin/topics/" + topicId + "/ai-examples";
        } catch (TopicAiExampleException ex) {
            bindingResult.reject("example", humanError(ex.getCode()));
            prepareEditor(model, topicId, null);
            return "admin/topic-ai-example-form";
        }
    }

    @PostMapping("/admin/topics/{topicId}/ai-examples/{exampleId}")
    String update(
            @PathVariable Long topicId,
            @PathVariable Long exampleId,
            @Valid @ModelAttribute("exampleForm") TopicAiExampleForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            prepareEditor(model, topicId, exampleId);
            return "admin/topic-ai-example-form";
        }
        try {
            exampleService.update(topicId, exampleId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Эталон обновлён");
            return "redirect:/admin/topics/" + topicId + "/ai-examples";
        } catch (TopicAiExampleException ex) {
            bindingResult.reject("example", humanError(ex.getCode()));
            prepareEditor(model, topicId, exampleId);
            return "admin/topic-ai-example-form";
        }
    }

    @PostMapping("/admin/topics/{topicId}/ai-examples/{exampleId}/delete")
    String delete(
            @PathVariable Long topicId,
            @PathVariable Long exampleId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            exampleService.delete(topicId, exampleId);
            redirectAttributes.addFlashAttribute("successMessage", "Эталон удалён");
        } catch (TopicAiExampleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "redirect:/admin/topics/" + topicId + "/ai-examples";
    }

    // --- вспомогательное ---

    /** Общие атрибуты редактора: тема, режим, справочники типов и правил FILL_IN. */
    private void prepareEditor(Model model, Long topicId, Long exampleId) {
        model.addAttribute("activeAdminNav", NAV);
        model.addAttribute("topic", contentGraph.getTopic(topicId));
        model.addAttribute("mode", exampleId == null ? "create" : "edit");
        model.addAttribute("exampleId", exampleId);
        model.addAttribute("questionTypes", QuestionType.values());
        model.addAttribute("fillModes", FillMatchMode.values());
    }

    /**
     * Пустая форма с преднастроенными строками: 4 варианта, 2 пары, 1 пропуск.
     * Даёт методисту готовый каркас — не нужно вручную добавлять первые строки.
     */
    private TopicAiExampleForm blankForm() {
        TopicAiExampleForm form = new TopicAiExampleForm();
        List<ChoiceOptionForm> options = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            options.add(new ChoiceOptionForm(String.valueOf((char) ('A' + i)), "", "", false));
        }
        form.setOptions(options);
        List<MatchingPairForm> pairs = new ArrayList<>();
        pairs.add(new MatchingPairForm("", "", "", ""));
        pairs.add(new MatchingPairForm("", "", "", ""));
        form.setMatchingPairs(pairs);
        List<FillAnswerForm> fills = new ArrayList<>();
        fills.add(new FillAnswerForm("", "", FillMatchMode.EXACT, null));
        form.setFillAnswers(fills);
        return form;
    }

    /** Преобразует сохранённый эталон в форму редактора (заполняет список под тип). */
    private TopicAiExampleForm toForm(TopicAiExampleResponse response) {
        TopicAiExampleForm form = blankForm();
        form.setQuestionType(QuestionType.valueOf(response.questionType()));
        form.setDifficulty(response.difficulty());
        form.setBodyRu(response.bodyRu());
        form.setBodyKk(response.bodyKk());
        form.setIncludeInAi(response.includeInAi());
        form.setInternalNote(response.internalNote());
        if (!response.options().isEmpty()) {
            form.setOptions(new ArrayList<>(response.options()));
        }
        if (!response.matchingPairs().isEmpty()) {
            form.setMatchingPairs(new ArrayList<>(response.matchingPairs()));
        }
        if (!response.fillAnswers().isEmpty()) {
            form.setFillAnswers(new ArrayList<>(response.fillAnswers()));
        }
        return form;
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

    /** Машинные коды ошибок эталона -> понятный методисту текст. */
    private String humanError(String code) {
        return switch (code) {
            case "example_limit_reached" ->
                    "Достигнут лимит эталонов на тему (" + TopicAiExampleService.MAX_EXAMPLES_PER_TOPIC + "). Удалите лишний, чтобы добавить новый";
            case "example_not_found" -> "Эталон не найден";
            case "example_topic_mismatch" -> "Эталон принадлежит другой теме";
            case "example_body_required" -> "Заполните текст вопроса на двух языках";
            case "example_difficulty_invalid" -> "Сложность должна быть от 1 до 5";
            case "choice_requires_two_options" -> "Нужно минимум два заполненных варианта";
            case "choice_option_text_required" -> "У каждого варианта заполните и RU, и KK";
            case "scq_requires_exactly_one_correct" -> "Для SCQ отметьте ровно один правильный вариант";
            case "mcq_requires_one_correct" -> "Для MCQ отметьте хотя бы один правильный вариант";
            case "matching_requires_two_pairs" -> "Для MATCHING нужно минимум две пары";
            case "matching_pair_required" -> "У каждой пары заполните левую и правую части на двух языках";
            case "fill_in_requires_answer" -> "Для FILL_IN добавьте хотя бы один пропуск с ответом";
            case "fill_in_answer_required" -> "У каждого пропуска заполните метку, ответ и правило";
            case "fill_in_tolerance_required" -> "Для правила NUMERIC_TOLERANCE укажите неотрицательный допуск";
            default -> "Эталон не сохранён: " + code;
        };
    }
}
