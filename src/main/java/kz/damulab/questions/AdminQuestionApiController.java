package kz.damulab.questions;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long gradeId,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) QuestionStatus status,
            @RequestParam(required = false) QuestionType type,
            @RequestParam(required = false) String query
    ) {
        return questionBank.listQuestions(subjectId, gradeId, topicId, status, type, query);
    }

    @GetMapping("/{id}")
    QuestionResponse question(@PathVariable Long id) {
        return questionBank.getQuestion(id);
    }

    /** Отдаёт двуязычное содержимое, ответы и лекцию только для admin-модалки. */
    @GetMapping("/{id}/preview")
    QuestionPreviewResponse preview(@PathVariable Long id) {
        return questionBank.getQuestionPreview(id);
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

    /** Генерирует мини-лекцию по сохранённому вопросу и сразу сохраняет её в проверяемую версию. */
    @PostMapping("/{id}/mini-lecture/generate")
    GeneratedQuestionMiniLectureResponse generateMiniLecture(@PathVariable Long id) {
        return questionBank.generateAndSaveMiniLecture(id);
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

    /**
     * Выполняет approve/publish независимо для каждой строки. Частичная ошибка не откатывает
     * успешные вопросы и возвращается в теле, чтобы таблица могла обновиться без перезагрузки.
     */
    @PostMapping("/bulk")
    QuestionBulkActionResponse bulk(@Valid @RequestBody QuestionBulkActionRequest request) {
        List<QuestionBulkActionItemResponse> items = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(request.questionIds())) {
            try {
                QuestionResponse question = switch (request.action()) {
                    case APPROVE -> questionBank.approve(id);
                    case PUBLISH -> questionBank.publish(id);
                };
                items.add(new QuestionBulkActionItemResponse(id, question, null));
            } catch (QuestionBankException ex) {
                items.add(new QuestionBulkActionItemResponse(id, null, ex.getCode()));
            }
        }
        int succeeded = (int) items.stream().filter(item -> item.error() == null).count();
        return new QuestionBulkActionResponse(
                request.action().name().toLowerCase(),
                succeeded,
                items.size() - succeeded,
                items
        );
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
    ResponseEntity<QuestionImportJobResponse> importQuestions(@RequestBody QuestionImportRequest request) {
        QuestionImportJobResponse created = questionBank.importQuestions(request);
        return ResponseEntity.created(URI.create("/api/admin/questions/imports/" + created.id())).body(created);
    }
}
