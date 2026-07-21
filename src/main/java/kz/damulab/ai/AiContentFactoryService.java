package kz.damulab.ai;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import kz.damulab.audit.AdminContentAuditService;
import kz.damulab.content.AtomicSkill;
import kz.damulab.content.AtomicSkillRepository;
import kz.damulab.content.Topic;
import kz.damulab.content.TopicAiExample;
import kz.damulab.content.TopicAiExampleRepository;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AiContentFactoryService {

    /**
     * Сколько эталонов максимум уходит в промпт (prompt cap, решение 2b: хранить до 6, слать ≤3).
     * Немного few-shot достаточно для стиля/scope; больше — лишние токены и риск копирования.
     */
    static final int MAX_EXAMPLES_IN_PROMPT = 3;

    /**
     * Бюджет символов на все эталоны в промпте. Защита от гигантских примеров: как только
     * добавление следующего эталона превысит бюджет — прекращаем добор. Ограничивает стоимость
     * и латентность независимо от {@link #MAX_EXAMPLES_IN_PROMPT}.
     */
    static final int EXAMPLES_CHAR_BUDGET = 6000;

    private final AiGenerationJobRunner runner;
    private final AiGenerationJobRepository jobs;
    private final AiGeneratedQuestionBatchRepository batches;
    private final AiGeneratedQuestionItemRepository items;
    private final TopicRepository topics;
    private final AtomicSkillRepository skills;
    private final TopicAiExampleRepository topicExamples;
    private final AppUserRepository users;
    private final QuestionBankService questionBank;
    private final AdminContentAuditService audit;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public AiContentFactoryService(
            AiGenerationJobRunner runner,
            AiGenerationJobRepository jobs,
            AiGeneratedQuestionBatchRepository batches,
            AiGeneratedQuestionItemRepository items,
            TopicRepository topics,
            AtomicSkillRepository skills,
            TopicAiExampleRepository topicExamples,
            AppUserRepository users,
            QuestionBankService questionBank,
            AdminContentAuditService audit,
            ObjectMapper objectMapper,
            EntityManager entityManager
    ) {
        this.runner = runner;
        this.jobs = jobs;
        this.batches = batches;
        this.items = items;
        this.topics = topics;
        this.skills = skills;
        this.topicExamples = topicExamples;
        this.users = users;
        this.questionBank = questionBank;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    /**
     * Создаёт AI job в статусе PENDING и сразу возвращает ответ.
     * Фактический вызов провайдера запускается асинхронно через {@link AiGenerationJobRunner#execute}
     * после коммита транзакции — чтобы job был виден в БД до начала выполнения.
     */
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
        audit.record("ai_job_created", "AiGenerationJob", job.getId(), job.getStatus().name());
        Long jobId = job.getId();
        // Запускаем провайдера после коммита: job уже в БД, polling сразу увидит его
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runner.execute(jobId);
            }
        });
        return getJob(jobId);
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

    /**
     * Перезапускает провайдер для failed job.
     * Job помечается RUNNING сразу (до коммита), провайдер запускается асинхронно после коммита.
     */
    @Transactional
    public AiGenerationJobResponse retry(Long id) {
        AiGenerationJob job = findJob(id);
        if (job.getStatus() != AiGenerationJobStatus.FAILED) {
            throw new AiContentFactoryException("ai_job_retry_requires_failed_status");
        }
        job.incrementRetry();
        job.markRunning();
        audit.record("ai_job_retried", "AiGenerationJob", job.getId(), job.getStatus().name());
        Long jobId = job.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runner.execute(jobId);
            }
        });
        return getJob(jobId);
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
                sanitizeInstruction(form.getInstruction()),
                selectExamplesForPrompt(topic.getId(), form.getExampleIds())
        );
    }

    /**
     * Отбирает эталоны темы для few-shot и превращает их в «тонкий» {@link AiExamplePayload}
     * (без id/PII). Логика отбора:
     * <ol>
     *   <li>берём только активные ({@code include_in_ai}) эталоны темы;</li>
     *   <li>если {@code selectedIds != null} — оставляем только выбранные методистом
     *       (пустой список => ни одного; см. семантику в {@link AiQuestionGenerationForm});</li>
     *   <li>ограничиваем количеством {@link #MAX_EXAMPLES_IN_PROMPT} и бюджетом
     *       {@link #EXAMPLES_CHAR_BUDGET} символов.</li>
     * </ol>
     * Порядок сохраняется как в БД (по id), поэтому «первые» эталоны приоритетнее при обрезке.
     */
    private List<AiExamplePayload> selectExamplesForPrompt(Long topicId, List<Long> selectedIds) {
        List<TopicAiExample> active = topicExamples.findByTopicIdAndIncludeInAiTrueOrderByIdAsc(topicId);
        // null => берём все активные (вызов без явного выбора); иначе фильтруем по выбранным id.
        Set<Long> allowed = selectedIds == null ? null : new LinkedHashSet<>(selectedIds);

        List<AiExamplePayload> result = new java.util.ArrayList<>();
        int usedChars = 0;
        for (TopicAiExample example : active) {
            if (result.size() >= MAX_EXAMPLES_IN_PROMPT) {
                break;
            }
            if (allowed != null && !allowed.contains(example.getId())) {
                continue;
            }
            // Оценка вклада эталона в промпт: тело RU/KK + сериализованный ключ ответа.
            int cost = length(example.getBodyRu()) + length(example.getBodyKk())
                    + length(example.getAnswerKeyJson());
            if (!result.isEmpty() && usedChars + cost > EXAMPLES_CHAR_BUDGET) {
                // Первый эталон пропускаем в бюджет всегда (иначе слишком большой единственный
                // пример полностью выключит few-shot); последующие — только если укладываемся.
                break;
            }
            usedChars += cost;
            result.add(new AiExamplePayload(
                    example.getQuestionType(),
                    example.getDifficulty(),
                    example.getBodyRu(),
                    example.getBodyKk(),
                    example.getAnswerKeyJson()
            ));
        }
        return result;
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
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
        Topic topic = topics.findById(id).orElseThrow(() -> new AiContentFactoryException("topic_not_found"));
        if (topic.isDeleted()) {
            throw new AiContentFactoryException("topic_not_found");
        }
        return topic;
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
