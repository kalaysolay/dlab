package kz.damulab.ai;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import kz.damulab.audit.AdminContentAuditService;
import kz.damulab.content.AtomicSkill;
import kz.damulab.content.AtomicSkillRepository;
import kz.damulab.content.Topic;
import kz.damulab.content.TopicRepository;
import kz.damulab.questions.ChoiceOptionForm;
import kz.damulab.questions.FillAnswerForm;
import kz.damulab.questions.MatchingPairForm;
import kz.damulab.questions.Question;
import kz.damulab.questions.QuestionBankService;
import kz.damulab.questions.QuestionForm;
import kz.damulab.questions.QuestionResponse;
import kz.damulab.questions.QuestionStatus;
import kz.damulab.questions.QuestionType;
import kz.damulab.users.AppUser;
import kz.damulab.users.AppUserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiContentFactoryService {

    private final AiProvider provider;
    private final AiGenerationJobRepository jobs;
    private final AiGeneratedQuestionBatchRepository batches;
    private final AiGeneratedQuestionItemRepository items;
    private final TopicRepository topics;
    private final AtomicSkillRepository skills;
    private final AppUserRepository users;
    private final QuestionBankService questionBank;
    private final AdminContentAuditService audit;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final AiDraftSchemaValidator draftSchemaValidator;

    public AiContentFactoryService(
            AiProvider provider,
            AiGenerationJobRepository jobs,
            AiGeneratedQuestionBatchRepository batches,
            AiGeneratedQuestionItemRepository items,
            TopicRepository topics,
            AtomicSkillRepository skills,
            AppUserRepository users,
            QuestionBankService questionBank,
            AdminContentAuditService audit,
            ObjectMapper objectMapper,
            EntityManager entityManager,
            AiDraftSchemaValidator draftSchemaValidator
    ) {
        this.provider = provider;
        this.jobs = jobs;
        this.batches = batches;
        this.items = items;
        this.topics = topics;
        this.skills = skills;
        this.users = users;
        this.questionBank = questionBank;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.draftSchemaValidator = draftSchemaValidator;
    }

    @Transactional
    public AiGenerationJobResponse createQuestionGenerationJob(AiQuestionGenerationForm form) {
        Topic topic = findTopic(form.getTopicId());
        AtomicSkill skill = resolveSkill(form.getAtomicSkillId(), topic);
        AiQuestionGenerationRequest request = buildRequest(form, topic, skill);
        AiGenerationJob job = jobs.save(new AiGenerationJob(
                topic,
                skill,
                form.getQuestionType(),
                form.getCount(),
                form.getDifficulty(),
                form.getLanguageMode(),
                request.methodistInstruction(),
                toJson(request),
                currentUser()
        ));
        runProvider(job, request);
        audit.record("ai_job_created", "AiGenerationJob", job.getId(), job.getStatus().name());
        return getJob(job.getId());
    }

    @Transactional(readOnly = true)
    public AiGenerationJobResponse getJob(Long id) {
        AiGenerationJob job = findJob(id);
        AiGeneratedQuestionBatch batch = batches.findByJobId(job.getId()).orElse(null);
        List<AiGeneratedQuestionItemResponse> itemResponses = batch == null
                ? List.of()
                : items.findByBatchIdOrderByIdAsc(batch.getId()).stream().map(this::toItemResponse).toList();
        return toJobResponse(job, batch, itemResponses);
    }

    @Transactional
    public AiGenerationJobResponse retry(Long id) {
        AiGenerationJob job = findJob(id);
        if (job.getStatus() != AiGenerationJobStatus.FAILED) {
            throw new AiContentFactoryException("ai_job_retry_requires_failed_status");
        }
        job.incrementRetry();
        runProvider(job, fromJson(job.getRequestPayloadJson(), AiQuestionGenerationRequest.class));
        audit.record("ai_job_retried", "AiGenerationJob", job.getId(), job.getStatus().name());
        return getJob(job.getId());
    }

    @Transactional
    public AiGeneratedQuestionItemResponse editItem(Long batchId, Long itemId, AiGeneratedQuestionItemEditForm form) {
        AiGeneratedQuestionItem item = findItemInBatch(batchId, itemId);
        if (item.getReviewStatus() == AiGeneratedItemReviewStatus.APPROVED || item.isDeleted()) {
            throw new AiContentFactoryException("ai_item_not_editable");
        }
        item.edit(form, currentUser());
        audit.record("ai_item_edited", "AiGeneratedQuestionItem", item.getId(), item.getQuestionType().name());
        return toItemResponse(item);
    }

    @Transactional
    public AiGeneratedQuestionItemResponse approveItem(Long batchId, Long itemId) {
        AiGeneratedQuestionItem item = findItemInBatch(batchId, itemId);
        if (item.getReviewStatus() == AiGeneratedItemReviewStatus.APPROVED || item.isDeleted()) {
            throw new AiContentFactoryException("ai_item_not_approvable");
        }
        QuestionResponse created = questionBank.createQuestion(toQuestionForm(item));
        Question questionRef = entityManager.getReference(Question.class, created.id());
        item.approve(questionRef, currentUser());
        audit.record("ai_item_approved", "AiGeneratedQuestionItem", item.getId(), "question:" + created.id());
        return toItemResponse(item);
    }

    @Transactional
    public AiGeneratedQuestionItemResponse deleteItem(Long batchId, Long itemId) {
        AiGeneratedQuestionItem item = findItemInBatch(batchId, itemId);
        if (item.getReviewStatus() == AiGeneratedItemReviewStatus.APPROVED) {
            throw new AiContentFactoryException("ai_item_approved_not_deletable");
        }
        item.delete(currentUser());
        audit.record("ai_item_deleted", "AiGeneratedQuestionItem", item.getId(), item.getQuestionType().name());
        return toItemResponse(item);
    }

    AiQuestionGenerationRequest buildOutboundRequestForTest(AiQuestionGenerationForm form) {
        Topic topic = findTopic(form.getTopicId());
        AtomicSkill skill = resolveSkill(form.getAtomicSkillId(), topic);
        return buildRequest(form, topic, skill);
    }

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
        }
    }

    private AiQuestionGenerationRequest buildRequest(AiQuestionGenerationForm form, Topic topic, AtomicSkill skill) {
        return new AiQuestionGenerationRequest(
                topic.getSubject().getTitleRu(),
                topic.getSubject().getTitleKk(),
                topic.getGrade().getGradeNo(),
                topic.getTitleRu(),
                topic.getTitleKk(),
                skill == null ? null : skill.getTitleRu(),
                skill == null ? null : skill.getTitleKk(),
                form.getQuestionType(),
                form.getDifficulty(),
                form.getCount(),
                form.getLanguageMode(),
                sanitizeInstruction(form.getInstruction())
        );
    }

    private QuestionForm toQuestionForm(AiGeneratedQuestionItem item) {
        AiGenerationJob job = item.getBatch().getJob();
        var jobTopic = job.getTopic();
        QuestionForm form = new QuestionForm();
        form.setSubjectId(jobTopic.getSubject().getId());
        form.setTopicIds(new java.util.ArrayList<>(java.util.List.of(jobTopic.getId())));
        form.setGradeIds(new java.util.ArrayList<>(java.util.List.of(jobTopic.getGrade().getId())));
        form.setAtomicSkillId(job.getAtomicSkill() == null ? null : job.getAtomicSkill().getId());
        form.setType(item.getQuestionType());
        form.setDifficulty(item.getDifficulty());
        form.setBodyRu(item.getBodyRu());
        form.setBodyKk(item.getBodyKk());
        form.setExplanationRu(item.getExplanationRu());
        form.setExplanationKk(item.getExplanationKk());
        form.setSource(item.getSource());
        form.setStatus(QuestionStatus.NEEDS_REVIEW);
        if (item.getQuestionType() == QuestionType.SCQ || item.getQuestionType() == QuestionType.MCQ) {
            form.setOptions(fromJson(item.getOptionsJson(), new TypeReference<List<ChoiceOptionForm>>() {
            }));
        } else if (item.getQuestionType() == QuestionType.MATCHING) {
            form.setMatchingPairs(fromJson(item.getOptionsJson(), new TypeReference<List<MatchingPairForm>>() {
            }));
        } else if (item.getQuestionType() == QuestionType.FILL_IN) {
            form.setFillAnswers(fromJson(item.getAnswerKeyJson(), new TypeReference<List<FillAnswerForm>>() {
            }));
        }
        return form;
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

    private AiGenerationJobResponse toJobResponse(
            AiGenerationJob job,
            AiGeneratedQuestionBatch batch,
            List<AiGeneratedQuestionItemResponse> itemResponses
    ) {
        return new AiGenerationJobResponse(
                job.getId(),
                job.getStatus().name().toLowerCase(Locale.ROOT),
                job.getProviderName(),
                job.getModelName(),
                job.getTopic().getId(),
                job.getTopic().getTitleRu(),
                job.getAtomicSkill() == null ? null : job.getAtomicSkill().getId(),
                job.getAtomicSkill() == null ? null : job.getAtomicSkill().getTitleRu(),
                job.getQuestionType().name(),
                job.getRequestedCount(),
                job.getDifficulty(),
                job.getLanguageMode().name(),
                job.getInstruction(),
                job.getRetryCount(),
                job.getErrorCode(),
                job.getErrorMessage(),
                batch == null ? null : batch.getId(),
                itemResponses,
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getCompletedAt()
        );
    }

    private AiGeneratedQuestionItemResponse toItemResponse(AiGeneratedQuestionItem item) {
        return new AiGeneratedQuestionItemResponse(
                item.getId(),
                item.getBatch().getId(),
                item.getReviewStatus().name().toLowerCase(Locale.ROOT),
                item.getQuestionType().name(),
                item.getDifficulty(),
                item.getBodyRu(),
                item.getBodyKk(),
                item.getExplanationRu(),
                item.getExplanationKk(),
                item.getSource(),
                item.getOptionsJson(),
                item.getAnswerKeyJson(),
                item.getQualityScore(),
                item.getQualityNotes(),
                item.getFlagsJson(),
                item.getCreatedQuestion() == null ? null : item.getCreatedQuestion().getId()
        );
    }

    private AiGeneratedQuestionItem findItemInBatch(Long batchId, Long itemId) {
        AiGeneratedQuestionItem item = items.findById(itemId)
                .orElseThrow(() -> new AiContentFactoryException("ai_item_not_found"));
        if (!item.getBatch().getId().equals(batchId)) {
            throw new AiContentFactoryException("ai_item_batch_mismatch");
        }
        return item;
    }

    private AiGenerationJob findJob(Long id) {
        return jobs.findById(id).orElseThrow(() -> new AiContentFactoryException("ai_job_not_found"));
    }

    private Topic findTopic(Long id) {
        return topics.findById(id).orElseThrow(() -> new AiContentFactoryException("topic_not_found"));
    }

    private AtomicSkill resolveSkill(Long id, Topic topic) {
        if (id == null) {
            return null;
        }
        AtomicSkill skill = skills.findById(id).orElseThrow(() -> new AiContentFactoryException("skill_not_found"));
        if (!skill.getTopic().getId().equals(topic.getId())) {
            throw new AiContentFactoryException("skill_topic_mismatch");
        }
        return skill;
    }

    private AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return users.findByEmailIgnoreCase(authentication.getName()).orElse(null);
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

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new AiContentFactoryException("ai_payload_invalid");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String sanitizeInstruction(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed
                .replaceAll("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", "[redacted-email]")
                .replaceAll("(?i)\\b(student|parent|user|child|link)_?id\\s*[:=]\\s*[a-z0-9-]+", "$1_id=[redacted-id]")
                .replaceAll("(?i)\\blink\\s*code\\s*[:=]\\s*[a-z0-9-]+", "link code=[redacted-code]")
                .replaceAll("(?<!\\d)(?:\\+?7|8)[\\s\\-()]?\\d{3}[\\s\\-()]?\\d{3}[\\s\\-()]?\\d{2}[\\s\\-()]?\\d{2}(?!\\d)", "[redacted-phone]");
    }
}
