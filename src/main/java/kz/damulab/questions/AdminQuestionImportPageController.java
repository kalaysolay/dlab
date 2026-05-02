package kz.damulab.questions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import kz.damulab.content.ContentGraphService;

@Controller
public class AdminQuestionImportPageController {

    private final QuestionBankService questionBank;
    private final ContentGraphService contentGraph;
    private final ObjectMapper objectMapper;

    public AdminQuestionImportPageController(
            QuestionBankService questionBank,
            ContentGraphService contentGraph,
            ObjectMapper objectMapper
    ) {
        this.questionBank = questionBank;
        this.contentGraph = contentGraph;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/admin/questions/import")
    String importPage(Model model) {
        addModel(model);
        model.addAttribute("payload", samplePayload());
        return "admin/question-import";
    }

    @PostMapping("/admin/questions/import")
    String importQuestions(@RequestParam String payload, Model model) {
        addModel(model);
        model.addAttribute("payload", payload);
        try {
            QuestionImportRequest request = objectMapper.readValue(payload, QuestionImportRequest.class);
            QuestionImportJobResponse job = questionBank.importQuestions(request);
            model.addAttribute("job", job);
            if (job.errorRows() == 0) {
                model.addAttribute("successMessage", "Импорт завершен: " + job.importedRows() + " строк");
            } else {
                model.addAttribute("errorMessage", "Импорт завершен с ошибками: " + job.errorRows() + " строк");
            }
        } catch (JsonProcessingException ex) {
            model.addAttribute("errorMessage", "JSON не прочитан: проверьте структуру payload");
        } catch (QuestionBankException ex) {
            model.addAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "admin/question-import";
    }

    @PostMapping("/admin/questions/import/excel")
    String importExcel(@RequestParam("file") MultipartFile file, Model model) {
        addModel(model);
        model.addAttribute("payload", samplePayload());
        try {
            QuestionImportJobResponse job = questionBank.importQuestionsFromExcel(file);
            model.addAttribute("job", job);
            if (job.errorRows() == 0) {
                model.addAttribute("successMessage", "Excel импорт завершен: " + job.importedRows() + " строк");
            } else {
                model.addAttribute("errorMessage", "Excel импорт завершен с ошибками: " + job.errorRows() + " строк");
            }
        } catch (QuestionBankException ex) {
            model.addAttribute("errorMessage", humanError(ex.getCode()));
        }
        return "admin/question-import";
    }

    private void addModel(Model model) {
        model.addAttribute("activeAdminNav", "question-import");
    }

    private String samplePayload() {
        Long topicId = contentGraph.listTopics(resolveSubjectId(), resolveGradeId()).stream()
                .findFirst()
                .map(topic -> topic.id())
                .orElse(null);
        return """
                {
                  "questions": [
                    {
                      "topicId": %s,
                      "type": "SCQ",
                      "difficulty": 2,
                      "bodyRu": "Найдите 10%% от 200.",
                      "bodyKk": "200 санының 10 пайызын табыңыз.",
                      "source": "JSON import",
                      "status": "NEEDS_REVIEW",
                      "options": [
                        {"label": "A", "textRu": "10", "textKk": "10", "correct": false},
                        {"label": "B", "textRu": "20", "textKk": "20", "correct": true},
                        {"label": "C", "textRu": "30", "textKk": "30", "correct": false}
                      ]
                    }
                  ]
                }
                """.formatted(topicId == null ? "null" : topicId.toString());
    }

    private Long resolveSubjectId() {
        return contentGraph.listSubjects().stream().findFirst().orElseThrow().id();
    }

    private Long resolveGradeId() {
        return contentGraph.listGrades().stream()
                .filter(grade -> Integer.valueOf(4).equals(grade.gradeNo()))
                .findFirst()
                .or(() -> contentGraph.listGrades().stream().findFirst())
                .orElseThrow()
                .id();
    }

    private String humanError(String code) {
        return switch (code) {
            case "question_import_empty" -> "JSON должен содержать массив questions";
            case "question_import_file_required" -> "Выберите Excel-файл";
            case "question_import_excel_invalid" -> "Excel-файл не прочитан";
            default -> "Импорт не выполнен: " + code;
        };
    }
}
