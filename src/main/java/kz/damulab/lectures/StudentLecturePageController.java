package kz.damulab.lectures;

import java.security.Principal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Серверные страницы ученического каталога лекций.
 *
 * <p>Идентификатор ученика всегда берётся из {@link Principal}; браузер передаёт
 * только лекцию и ответы, поэтому изменить чужой прогресс невозможно.</p>
 */
@Controller
public class StudentLecturePageController {

    private final StudentLectureService studentLectureService;
    private final ObjectMapper objectMapper;

    public StudentLecturePageController(
            StudentLectureService studentLectureService,
            ObjectMapper objectMapper
    ) {
        this.studentLectureService = studentLectureService;
        this.objectMapper = objectMapper;
    }

    /** Показывает каталог пар «предмет + класс», по которым опубликованы лекции. */
    @GetMapping("/student/lectures")
    String lectures(Model model) {
        model.addAttribute("lectureSubjectGrades", studentLectureService.listSubjectGrades());
        model.addAttribute("adminPreview", false);
        return "student/lectures";
    }

    /** Показывает отсортированные лекции выбранных предмета и класса с личными статусами. */
    @GetMapping("/student/lectures/subjects/{subjectId}/grades/{gradeId}")
    String subjectGradeLectures(
            Principal principal,
            @PathVariable Long subjectId,
            @PathVariable Long gradeId,
            Model model
    ) {
        model.addAttribute(
                "subjectPage",
                studentLectureService.subjectGradePage(principal.getName(), subjectId, gradeId)
        );
        return "student/lecture-subject";
    }

    /** Открывает лекцию и отмечает её начатой при первом посещении. */
    @GetMapping("/student/lectures/{id}")
    String lecture(
            Principal principal,
            @PathVariable Long id,
            Locale locale,
            Model model
    ) {
        StudentLectureReaderView reader = studentLectureService.openLecture(
                principal.getName(),
                id,
                locale.getLanguage()
        );
        model.addAttribute("reader", reader);
        model.addAttribute("lecture", reader.lecture());
        model.addAttribute("adminPreview", false);
        return "student/lecture";
    }

    /**
     * Принимает одну полную попытку теста и возвращает ученика к лекции.
     * Результат хранится во flash-атрибутах, чтобы обновление страницы не повторяло POST.
     */
    @PostMapping("/student/lectures/{id}/checkpoints")
    String submitCheckpoints(
            Principal principal,
            @PathVariable Long id,
            Locale locale,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        List<LectureCheckpointQuestionView> questions = studentLectureService
                .questionsForSubmission(id, locale.getLanguage());
        Map<Long, JsonNode> answers = new LinkedHashMap<>();
        for (LectureCheckpointQuestionView question : questions) {
            answers.put(question.id(), answerFromRequest(question, request));
        }

        LectureCheckpointAttemptResult result = studentLectureService.submitCheckpointAttempt(
                principal.getName(),
                id,
                answers
        );
        redirectAttributes.addFlashAttribute("checkpointResult", result);
        return "redirect:/student/lectures/" + id;
    }

    /** Завершает лекцию, если серверная проверка разрешает переход в DONE. */
    @PostMapping("/student/lectures/{id}/complete")
    String completeLecture(
            Principal principal,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            studentLectureService.completeLecture(principal.getName(), id);
            redirectAttributes.addFlashAttribute("lectureCompleted", true);
        } catch (LectureException exception) {
            if (!"lecture_checkpoint_required".equals(exception.getCode())) {
                throw exception;
            }
            redirectAttributes.addFlashAttribute("lectureCompletionBlocked", true);
        }
        return "redirect:/student/lectures/" + id;
    }

    /** Преобразует поля HTML-формы в формат существующего серверного AnswerChecker. */
    private JsonNode answerFromRequest(LectureCheckpointQuestionView question, HttpServletRequest request) {
        if ("SCQ".equals(question.type()) || "MCQ".equals(question.type())) {
            return objectMapper.valueToTree(Map.of("selected", values(request, "answer_" + question.id())));
        }
        if ("MATCHING".equals(question.type())) {
            return objectMapper.valueToTree(Map.of("pairs", matchingValues(question, request)));
        }
        if ("FILL_IN".equals(question.type())) {
            return objectMapper.valueToTree(Map.of("answers", fillValues(question, request)));
        }
        return objectMapper.createObjectNode();
    }

    /** Возвращает непустые значения radio/checkbox-группы. */
    private List<String> values(HttpServletRequest request, String name) {
        String[] submittedValues = request.getParameterValues(name);
        if (submittedValues == null) {
            return List.of();
        }
        return Arrays.stream(submittedValues)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    /** Собирает выбранные пары сопоставления по порядку левой колонки. */
    private Map<String, String> matchingValues(
            LectureCheckpointQuestionView question,
            HttpServletRequest request
    ) {
        Map<String, String> pairs = new LinkedHashMap<>();
        for (int index = 0; index < question.matchingLeft().size(); index++) {
            String right = request.getParameter("match_" + question.id() + "_" + index);
            if (right != null && !right.isBlank()) {
                pairs.put(question.matchingLeft().get(index).value(), right);
            }
        }
        return pairs;
    }

    /** Собирает ответы FILL_IN по исходным placeholder вида [[1]]. */
    private Map<String, String> fillValues(
            LectureCheckpointQuestionView question,
            HttpServletRequest request
    ) {
        Map<String, String> answers = new LinkedHashMap<>();
        for (int index = 0; index < question.fillPlaceholders().size(); index++) {
            String value = request.getParameter("fill_" + question.id() + "_" + index);
            if (value != null && !value.isBlank()) {
                answers.put(question.fillPlaceholders().get(index), value);
            }
        }
        return answers;
    }
}
