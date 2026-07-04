package kz.damulab.quiz;

import java.security.Principal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import kz.damulab.testing.TestType;

@Controller
public class QuizPageController {

    private final QuizService quizService;

    public QuizPageController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping("/student/quiz")
    String hub(Model model, Locale locale) {
        populateHub(model, locale);
        if (!model.containsAttribute("createQuizRoomRequest")) {
            CreateQuizRoomRequest form = new CreateQuizRoomRequest();
            form.setLanguage(defaultLanguage(locale));
            defaultSchoolSubject(model).ifPresent(subject -> {
                form.setSubjectId(subject.id());
                subject.grades().stream().findFirst().ifPresent(grade -> form.setGradeId(grade.id()));
            });
            model.addAttribute("createQuizRoomRequest", form);
        }
        return "student/quiz-hub";
    }

    @PostMapping("/student/quiz/rooms")
    String create(
            Principal principal,
            @Valid @ModelAttribute("createQuizRoomRequest") CreateQuizRoomRequest form,
            BindingResult bindingResult,
            Model model,
            Locale locale
    ) {
        if (bindingResult.hasErrors()) {
            populateHub(model, locale);
            return "student/quiz-hub";
        }
        try {
            QuizRoomResponse room = quizService.createRoom(principal.getName(), form);
            return "redirect:/student/quiz/rooms/" + room.code();
        } catch (QuizException ex) {
            populateHub(model, locale);
            model.addAttribute("error", ex.getMessage());
            return "student/quiz-hub";
        }
    }

    @PostMapping("/student/quiz/rooms/join")
    String joinFromHub(Principal principal, HttpServletRequest request, Model model, Locale locale) {
        String code = request.getParameter("code");
        try {
            QuizRoomResponse room = quizService.join(code, principal.getName());
            return "redirect:/student/quiz/rooms/" + room.code();
        } catch (QuizException ex) {
            populateHub(model, locale);
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("joinCode", code);
            return "student/quiz-hub";
        }
    }

    @GetMapping("/student/quiz/rooms/{code}")
    String room(Principal principal, @PathVariable String code, Model model) {
        model.addAttribute("room", quizService.room(code, principal.getName()));
        return "student/quiz-room";
    }

    @PostMapping("/student/quiz/rooms/{code}/ready")
    String ready(Principal principal, @PathVariable String code) {
        quizService.ready(code, principal.getName());
        return "redirect:/student/quiz/rooms/" + code;
    }

    @PostMapping("/student/quiz/rooms/{code}/start")
    String start(Principal principal, @PathVariable String code) {
        quizService.start(code, principal.getName());
        return "redirect:/student/quiz/rooms/" + code;
    }

    @PostMapping("/student/quiz/rooms/{code}/answers")
    String answer(Principal principal, @PathVariable String code, HttpServletRequest request) {
        QuizRoomResponse room = quizService.room(code, principal.getName());
        QuizRoundResponse activeRound = room.rounds().stream()
                .filter(round -> round.id().equals(room.activeRoundId()))
                .findFirst()
                .orElseThrow(() -> new QuizException("round_not_open"));
        JsonNode answer = answerFromRequest(activeRound, request);
        quizService.submitAnswer(code, principal.getName(), new SubmitQuizAnswerRequest(activeRound.id(), answer));
        QuizRoomResponse updated = quizService.room(code, principal.getName());
        if ("finished".equals(updated.status())) {
            return "redirect:/student/quiz/rooms/" + code + "/results";
        }
        return "redirect:/student/quiz/rooms/" + code;
    }

    @GetMapping("/student/quiz/rooms/{code}/results")
    String results(Principal principal, @PathVariable String code, Model model) {
        model.addAttribute("results", quizService.results(code, principal.getName()));
        return "student/quiz-results";
    }

    private void populateHub(Model model, Locale locale) {
        model.addAttribute("quizSetupCatalog", quizService.setupCatalog());
        model.addAttribute("defaultQuizLanguage", defaultLanguage(locale));
        model.addAttribute("testTypes", Arrays.stream(TestType.values()).toList());
    }

    private java.util.Optional<QuizSetupSubjectOption> defaultSchoolSubject(Model model) {
        QuizSetupCatalog catalog = (QuizSetupCatalog) model.asMap().get("quizSetupCatalog");
        return catalog == null ? java.util.Optional.empty() : catalog.subjects().stream().findFirst();
    }

    private String defaultLanguage(Locale locale) {
        return locale != null && "kk".equals(locale.getLanguage()) ? "kk" : "ru";
    }

    private JsonNode answerFromRequest(QuizRoundResponse round, HttpServletRequest request) {
        return switch (round.type()) {
            case "SCQ", "MCQ" -> quizService.choiceAnswer(values(request, "answer_" + round.id()));
            case "MATCHING" -> quizService.matchingAnswer(matchingValues(round, request));
            case "FILL_IN" -> quizService.fillAnswer(fillValues(round, request));
            default -> quizService.choiceAnswer(List.of());
        };
    }

    private List<String> values(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        return values == null ? List.of() : Arrays.stream(values).filter(value -> !value.isBlank()).toList();
    }

    private Map<String, String> matchingValues(QuizRoundResponse round, HttpServletRequest request) {
        Map<String, String> pairs = new LinkedHashMap<>();
        for (int index = 0; index < round.matchingLeft().size(); index++) {
            String right = request.getParameter("match_" + round.id() + "_" + index);
            if (right != null && !right.isBlank()) {
                pairs.put(round.matchingLeft().get(index).value(), right);
            }
        }
        return pairs;
    }

    private Map<String, String> fillValues(QuizRoundResponse round, HttpServletRequest request) {
        Map<String, String> answers = new LinkedHashMap<>();
        for (int index = 0; index < round.fillPlaceholders().size(); index++) {
            String value = request.getParameter("fill_" + round.id() + "_" + index);
            if (value != null && !value.isBlank()) {
                answers.put(round.fillPlaceholders().get(index), value);
            }
        }
        return answers;
    }
}
