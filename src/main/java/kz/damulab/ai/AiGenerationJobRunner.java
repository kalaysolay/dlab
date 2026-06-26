package kz.damulab.ai;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.damulab.audit.AdminContentAuditService;
import kz.damulab.questions.QuestionType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Выполняет AI job в фоновом потоке (executor "aiJobExecutor").
 *
 * Вызывается из {@link AiContentFactoryService} через TransactionSynchronization.afterCommit(),
 * гарантируя, что job уже сохранён в БД до начала работы runner.
 *
 * Каждый вызов execute() открывает собственную транзакцию — HTTP-запрос к этому моменту уже завершён.
 * SecurityContext в async-потоке пуст, поэтому аудит записывается от имени "system".
 */
@Component
public class AiGenerationJobRunner {

    private static final Logger log = LoggerFactory.getLogger(AiGenerationJobRunner.class);

    private final AiProvider provider;
    private final AiGenerationJobRepository jobs;
    private final AiGeneratedQuestionBatchRepository batches;
    private final AiGeneratedQuestionItemRepository items;
    private final AiDraftSchemaValidator draftSchemaValidator;
    private final ObjectMapper objectMapper;
    private final AdminContentAuditService audit;

    public AiGenerationJobRunner(
            AiProvider provider,
            AiGenerationJobRepository jobs,
            AiGeneratedQuestionBatchRepository batches,
            AiGeneratedQuestionItemRepository items,
            AiDraftSchemaValidator draftSchemaValidator,
            ObjectMapper objectMapper,
            AdminContentAuditService audit
    ) {
        this.provider = provider;
        this.jobs = jobs;
        this.batches = batches;
        this.items = items;
        this.draftSchemaValidator = draftSchemaValidator;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    /**
     * Загружает job по id, вызывает AI-провайдер и сохраняет результаты.
     * Статус job: PENDING → RUNNING → SUCCEEDED|FAILED.
     *
     * Метод @Async: вызов немедленно возвращается вызывающему потоку,
     * а фактическое выполнение идёт в пуле "aiJobExecutor".
     */
    @Async("aiJobExecutor")
    @Transactional
    public void execute(Long jobId) {
        AiGenerationJob job = jobs.findById(jobId).orElseThrow(
                () -> new IllegalStateException("AI job не найден: " + jobId)
        );
        AiQuestionGenerationRequest request = fromJson(job.getRequestPayloadJson(), AiQuestionGenerationRequest.class);
        runProvider(job, request);
        audit.record("ai_job_completed", "AiGenerationJob", job.getId(), job.getStatus().name());
    }

    /**
     * Вызывает провайдера и сохраняет черновики вопросов.
     * При успехе — помечает job как SUCCEEDED, при сбое провайдера — FAILED.
     */
    private void runProvider(AiGenerationJob job, AiQuestionGenerationRequest request) {
        job.markRunning();
        try {
            AiQuestionGenerationResult result = provider.generateQuestions(request);
            AiGeneratedQuestionBatch batch = batches.save(new AiGeneratedQuestionBatch(job));
            for (AiGeneratedQuestionDraft draft : result.questions()) {
                draftSchemaValidator.validate(draft);
                items.save(new AiGeneratedQuestionItem(
                        batch,
                        draft,
                        toOptionsJson(draft),
                        toAnswerKeyJson(draft),
                        toJson(draft.flags())
                ));
            }
            job.markSucceeded(result.providerName(), result.modelName());
        } catch (AiProviderException ex) {
            job.markFailed(ex.getCode(), ex.getMessage());
            log.warn("AI job {}: провайдер вернул ошибку — {}", job.getId(), ex.getMessage());
        }
    }

    private String toOptionsJson(AiGeneratedQuestionDraft draft) {
        if (draft.questionType() == QuestionType.MATCHING) {
            return toJson(draft.matchingPairs().stream()
                    .map(pair -> Map.of(
                            "leftRu", pair.leftRu(),
                            "leftKk", pair.leftKk(),
                            "rightRu", pair.rightRu(),
                            "rightKk", pair.rightKk()
                    ))
                    .toList());
        }
        if (draft.questionType() == QuestionType.FILL_IN) {
            return toJson(List.of());
        }
        return toJson(draft.options().stream()
                .map(option -> Map.<String, Object>of(
                        "label", option.label().toUpperCase(Locale.ROOT),
                        "textRu", option.textRu(),
                        "textKk", option.textKk(),
                        "correct", option.correct()
                ))
                .toList());
    }

    private String toAnswerKeyJson(AiGeneratedQuestionDraft draft) {
        return switch (draft.questionType()) {
            case SCQ, MCQ -> toJson(draft.options().stream()
                    .filter(AiGeneratedChoiceOption::correct)
                    .map(option -> option.label().toUpperCase(Locale.ROOT))
                    .toList());
            case MATCHING -> toJson(Map.of("pairs", draft.matchingPairs().stream()
                    .map(pair -> Map.of("left", pair.leftRu(), "right", pair.rightRu()))
                    .toList()));
            case FILL_IN -> toJson(draft.fillAnswers().stream()
                    .map(answer -> {
                        Map<String, Object> value = new java.util.LinkedHashMap<>();
                        value.put("placeholder", answer.placeholder());
                        value.put("answer", answer.answer());
                        value.put("matchMode", answer.matchMode());
                        value.put("tolerance", answer.tolerance());
                        return value;
                    })
                    .toList());
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new AiContentFactoryException("ai_payload_invalid");
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new AiContentFactoryException("ai_payload_invalid");
        }
    }
}
