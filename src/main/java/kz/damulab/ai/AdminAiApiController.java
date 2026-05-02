package kz.damulab.ai;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai")
public class AdminAiApiController {

    private final AiContentFactoryService aiContentFactory;

    public AdminAiApiController(AiContentFactoryService aiContentFactory) {
        this.aiContentFactory = aiContentFactory;
    }

    @PostMapping("/questions/generate")
    ResponseEntity<AiGenerationJobResponse> generateQuestions(@Valid @RequestBody AiQuestionGenerationForm form) {
        AiGenerationJobResponse created = aiContentFactory.createQuestionGenerationJob(form);
        return ResponseEntity.created(URI.create("/api/admin/ai/jobs/" + created.id())).body(created);
    }

    @GetMapping("/jobs/{jobId}")
    AiGenerationJobResponse job(@PathVariable Long jobId) {
        return aiContentFactory.getJob(jobId);
    }

    @PostMapping("/jobs/{jobId}/retry")
    AiGenerationJobResponse retry(@PathVariable Long jobId) {
        return aiContentFactory.retry(jobId);
    }

    @PostMapping("/batches/{batchId}/items/{itemId}/approve")
    AiGeneratedQuestionItemResponse approveItem(@PathVariable Long batchId, @PathVariable Long itemId) {
        return aiContentFactory.approveItem(batchId, itemId);
    }

    @PutMapping("/batches/{batchId}/items/{itemId}")
    AiGeneratedQuestionItemResponse editItem(
            @PathVariable Long batchId,
            @PathVariable Long itemId,
            @Valid @RequestBody AiGeneratedQuestionItemEditForm form
    ) {
        return aiContentFactory.editItem(batchId, itemId, form);
    }

    @PostMapping("/batches/{batchId}/items/{itemId}/delete")
    AiGeneratedQuestionItemResponse deleteItem(@PathVariable Long batchId, @PathVariable Long itemId) {
        return aiContentFactory.deleteItem(batchId, itemId);
    }
}
