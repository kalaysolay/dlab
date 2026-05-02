package kz.damulab.questions;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/questions")
public class AdminQuestionApiController {

    private final QuestionBankService questionBank;

    public AdminQuestionApiController(QuestionBankService questionBank) {
        this.questionBank = questionBank;
    }

    @GetMapping
    List<QuestionResponse> questions(
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) QuestionStatus status,
            @RequestParam(required = false) QuestionType type,
            @RequestParam(required = false) String query
    ) {
        return questionBank.listQuestions(topicId, status, type, query);
    }

    @GetMapping("/{id}")
    QuestionResponse question(@PathVariable Long id) {
        return questionBank.getQuestion(id);
    }

    @GetMapping("/health")
    QuestionHealthSummaryResponse health(@RequestParam(required = false) QuestionQualityFilter quality) {
        return questionBank.listQuestionHealth(quality);
    }

    @PostMapping
    ResponseEntity<QuestionResponse> createQuestion(@Valid @RequestBody QuestionForm form) {
        QuestionResponse created = questionBank.createQuestion(form);
        return ResponseEntity.created(URI.create("/api/admin/questions/" + created.id())).body(created);
    }

    @PostMapping("/mini-lecture/generate")
    MiniLectureDraftResponse generateMiniLecture(@RequestBody QuestionForm form) {
        return questionBank.composeMiniLectureDraft(form);
    }

    @PatchMapping("/{id}")
    QuestionResponse updateQuestion(@PathVariable Long id, @Valid @RequestBody QuestionForm form) {
        return questionBank.updateQuestion(id, form);
    }

    @PostMapping("/{id}/approve")
    QuestionResponse approve(@PathVariable Long id) {
        return questionBank.approve(id);
    }

    @PostMapping("/{id}/publish")
    QuestionResponse publish(@PathVariable Long id) {
        return questionBank.publish(id);
    }

    @PostMapping("/{id}/archive")
    QuestionResponse archive(@PathVariable Long id) {
        return questionBank.archive(id);
    }

    @PostMapping("/{id}/flag")
    QuestionResponse flag(@PathVariable Long id, @RequestBody(required = false) FlagQuestionRequest request) {
        return questionBank.flagForReview(id, request == null ? null : request.getReason());
    }

    @GetMapping("/{id}/flags")
    List<QuestionFlagResponse> flags(@PathVariable Long id) {
        return questionBank.listQuestionFlags(id);
    }

    @PostMapping("/{id}/flags")
    ResponseEntity<QuestionFlagResponse> createFlag(
            @PathVariable Long id,
            @RequestBody QuestionFlagRequest request
    ) {
        QuestionFlagResponse created = questionBank.createQuestionFlag(id, request.getSource(), request.getReason());
        return ResponseEntity.created(URI.create("/api/admin/questions/" + id + "/flags/" + created.id())).body(created);
    }

    @PostMapping("/imports")
    ResponseEntity<QuestionImportJobResponse> importQuestions(@Valid @RequestBody QuestionImportRequest request) {
        QuestionImportJobResponse created = questionBank.importQuestions(request);
        return ResponseEntity.created(URI.create("/api/admin/questions/imports/" + created.id())).body(created);
    }
}
