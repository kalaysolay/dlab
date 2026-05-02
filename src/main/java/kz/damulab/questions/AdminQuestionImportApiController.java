package kz.damulab.questions;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestPart;

@RestController
@RequestMapping("/api/admin/question-imports")
public class AdminQuestionImportApiController {

    private final QuestionBankService questionBank;

    public AdminQuestionImportApiController(QuestionBankService questionBank) {
        this.questionBank = questionBank;
    }

    @PostMapping
    ResponseEntity<QuestionImportJobResponse> importQuestions(@Valid @RequestBody QuestionImportRequest request) {
        QuestionImportJobResponse created = questionBank.importQuestions(request);
        return ResponseEntity.created(URI.create("/api/admin/question-imports/" + created.id())).body(created);
    }

    @PostMapping("/excel")
    ResponseEntity<QuestionImportJobResponse> importExcel(@RequestPart("file") MultipartFile file) {
        QuestionImportJobResponse created = questionBank.importQuestionsFromExcel(file);
        return ResponseEntity.created(URI.create("/api/admin/question-imports/" + created.id())).body(created);
    }
}
