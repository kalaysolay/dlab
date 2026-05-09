package kz.damulab.testing;

import java.security.Principal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import kz.damulab.content.GradeRepository;
import kz.damulab.content.SubjectRepository;
import kz.damulab.gamification.AchievementUnlockPayload;

@Controller
public class StudentTestingPageController {

    private final TestingHubService testingHub;
    private final SubjectRepository subjects;
    private final GradeRepository grades;
    private final TestStartAvailabilityService testStartAvailability;

    public StudentTestingPageController(
            TestingHubService testingHub,
            SubjectRepository subjects,
            GradeRepository grades,
            TestStartAvailabilityService testStartAvailability
    ) {
        this.testingHub = testingHub;
        this.subjects = subjects;
        this.grades = grades;
        this.testStartAvailability = testStartAvailability;
    }

    @GetMapping("/student/tests")
    String tests(Principal principal, Model model) {
        populateTestStart(model, principal.getName());
        if (!model.containsAttribute("startTestSessionRequest")) {
            StartTestSessionRequest form = new StartTestSessionRequest();
            List<AvailableSubjectOption> availability = testStartAvailability.loadAvailability();
            if (!availability.isEmpty()) {
                AvailableSubjectOption preferred = availability.stream()
                        .filter(option -> isMathSubject(option.id()))
                        .findFirst()
                        .orElseGet(() -> availability.get(0));
                form.setSubjectId(preferred.id());
                AvailableGradeOption preferredGrade = preferred.grades().stream()
                        .filter(grade -> grade.gradeNo() == 4)
                        .findFirst()
                        .orElseGet(() -> preferred.grades().get(0));
                form.setGradeId(preferredGrade.id());
            } else {
                subjects.findAllByOrderByTitleRuAsc().stream().findFirst().ifPresent(subject -> form.setSubjectId(subject.getId()));
                grades.findAllByOrderByGradeNoAsc().stream()
                        .filter(grade -> Integer.valueOf(4).equals(grade.getGradeNo()))
                        .findFirst()
                        .or(() -> grades.findAllByOrderByGradeNoAsc().stream().findFirst())
                        .ifPresent(grade -> form.setGradeId(grade.getId()));
            }
            model.addAttribute("startTestSessionRequest", form);
        }
        return "student/tests";
    }

    @PostMapping("/student/test-sessions")
    String start(
            Principal principal,
            @Valid @ModelAttribute("startTestSessionRequest") StartTestSessionRequest form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            populateTestStart(model, principal.getName());
            return "student/tests";
        }
        try {
            TestSessionResponse session = testingHub.startSession(principal.getName(), form);
            return "redirect:/student/test-sessions/" + session.id();
        } catch (TestingHubException ex) {
            populateTestStart(model, principal.getName());
            model.addAttribute("error", ex.getMessage());
            return "student/tests";
        }
    }

    @GetMapping("/student/test-sessions/{sessionId}")
    String session(Principal principal, @PathVariable Long sessionId, Model model) {
        TestSessionResponse session = testingHub.getSession(principal.getName(), sessionId);
        if ("finished".equals(session.status())) {
            return "redirect:/student/test-sessions/" + sessionId + "/result";
        }
        model.addAttribute("testSession", session);
        return "student/test-session";
    }

    @PostMapping("/student/test-sessions/{sessionId}/finish")
    String finish(
            Principal principal,
            @PathVariable Long sessionId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        TestSessionResponse session = testingHub.getSession(principal.getName(), sessionId);
        for (SessionQuestionResponse question : session.questions()) {
            JsonNode answer = answerFromRequest(question, request);
            testingHub.submitAnswer(principal.getName(), sessionId, new SubmitAnswerRequest(question.id(), answer));
        }
        TestResultResponse outcome = testingHub.finishSession(principal.getName(), sessionId);
        if (!outcome.newlyUnlockedAchievements().isEmpty()) {
            redirectAttributes.addFlashAttribute("achievementToasts", outcome.newlyUnlockedAchievements());
        }
        return "redirect:/student/test-sessions/" + sessionId + "/result";
    }

    @GetMapping("/student/test-sessions/{sessionId}/result")
    String result(Principal principal, @PathVariable Long sessionId, Model model) {
        model.addAttribute("result", testingHub.getResult(principal.getName(), sessionId));
        return "student/test-result";
    }

    private void populateTestStart(Model model, String studentEmail) {
        List<AvailableSubjectOption> availability = testStartAvailability.loadAvailability();
        model.addAttribute("testAvailability", availability);
        model.addAttribute("hasTestAvailability", !availability.isEmpty());
        model.addAttribute("subjects", subjects.findAllByOrderByTitleRuAsc());
        model.addAttribute("grades", grades.findAllByOrderByGradeNoAsc());
        model.addAttribute("testTypes", Arrays.stream(TestType.values()).toList());
        model.addAttribute("recentSessions", testingHub.recentSessions(studentEmail));
    }

    private boolean isMathSubject(long subjectId) {
        return subjects.findById(subjectId)
                .map(subject -> "math".equals(subject.getCode()))
                .orElse(false);
    }

    private JsonNode answerFromRequest(SessionQuestionResponse question, HttpServletRequest request) {
        return switch (question.type()) {
            case "SCQ", "MCQ" -> testingHub.choiceAnswer(values(request, "answer_" + question.id()));
            case "MATCHING" -> testingHub.matchingAnswer(matchingValues(question, request));
            case "FILL_IN" -> testingHub.fillAnswer(fillValues(question, request));
            default -> testingHub.choiceAnswer(List.of());
        };
    }

    private List<String> values(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        return values == null ? List.of() : Arrays.stream(values).filter(value -> !value.isBlank()).toList();
    }

    private Map<String, String> matchingValues(SessionQuestionResponse question, HttpServletRequest request) {
        Map<String, String> pairs = new LinkedHashMap<>();
        for (int index = 0; index < question.matchingLeft().size(); index++) {
            String right = request.getParameter("match_" + question.id() + "_" + index);
            if (right != null && !right.isBlank()) {
                pairs.put(question.matchingLeft().get(index).value(), right);
            }
        }
        return pairs;
    }

    private Map<String, String> fillValues(SessionQuestionResponse question, HttpServletRequest request) {
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
