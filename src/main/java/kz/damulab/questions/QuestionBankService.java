package kz.damulab.questions;

import java.math.BigDecimal;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import kz.damulab.audit.AdminContentAuditService;
import kz.damulab.content.AtomicSkill;
import kz.damulab.content.AtomicSkillRepository;
import kz.damulab.content.Topic;
import kz.damulab.content.TopicRepository;
import kz.damulab.users.AppUser;
import kz.damulab.users.AppUserRepository;

@Service
public class QuestionBankService {

    private final QuestionRepository questions;
    private final QuestionVersionRepository versions;
    private final TopicRepository topics;
    private final AtomicSkillRepository skills;
    private final AppUserRepository users;
    private final QuestionImportJobRepository importJobs;
    private final QuestionImportErrorRepository importErrors;
    private final QuestionFlagRepository questionFlags;
    private final ObjectMapper objectMapper;
    private final AdminContentAuditService audit;

    public QuestionBankService(
            QuestionRepository questions,
            QuestionVersionRepository versions,
            TopicRepository topics,
            AtomicSkillRepository skills,
            AppUserRepository users,
            QuestionImportJobRepository importJobs,
            QuestionImportErrorRepository importErrors,
            QuestionFlagRepository questionFlags,
            ObjectMapper objectMapper,
            AdminContentAuditService audit
    ) {
        this.questions = questions;
        this.versions = versions;
        this.topics = topics;
        this.skills = skills;
        this.users = users;
        this.importJobs = importJobs;
        this.importErrors = importErrors;
        this.questionFlags = questionFlags;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> listQuestions(
            Long subjectId,
            Long gradeId,
            Long topicId,
            QuestionStatus status,
            QuestionType type,
            String query
    ) {
        return questions.findAll(filter(subjectId, gradeId, topicId, status, type, query)).stream()
                .sorted((left, right) -> right.getUpdatedAt().compareTo(left.getUpdatedAt()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuestionResponse getQuestion(Long id) {
        return toResponse(findQuestion(id));
    }

    @Transactional(readOnly = true)
    public QuestionEditView getQuestionEditView(Long id) {
        Question question = findQuestion(id);
        QuestionVersion currentVersion = question.getCurrentVersion();
        if (currentVersion == null) {
            throw new QuestionBankException("question_version_not_found");
        }
        java.util.Optional<QuestionVersion> pending = pendingDraft(question);
        QuestionVersion source = pending.orElse(currentVersion);
        return new QuestionEditView(
                question.getId(),
                source.getVersionNo(),
                question.getStatus().apiValue(),
                source.getTopic().getSubject().getId(),
                source.getTopic().getGrade().getId(),
                toEditForm(question, source),
                pending.isPresent(),
                pending.map(QuestionVersion::getVersionNo).orElse(null),
                currentVersion.getVersionNo()
        );
    }

    private java.util.Optional<QuestionVersion> pendingDraft(Question question) {
        QuestionVersion current = question.getCurrentVersion();
        if (current == null) {
            return java.util.Optional.empty();
        }
        return versions.findTopByQuestionIdOrderByVersionNoDesc(question.getId())
                .filter(latest -> latest.getVersionNo() > current.getVersionNo())
                .filter(latest -> latest.getPublishedAt() == null);
    }

    @Transactional(readOnly = true)
    public QuestionHealthSummaryResponse listQuestionHealth(QuestionQualityFilter quality) {
        Map<Long, HealthAggregate> aggregates = new HashMap<>();
        for (Object[] row : versions.aggregateCurrentVersionHealth()) {
            Long questionId = ((Number) row[0]).longValue();
            Long versionId = ((Number) row[1]).longValue();
            int attempts = ((Number) row[2]).intValue();
            int incorrect = ((Number) row[3]).intValue();
            aggregates.put(questionId, new HealthAggregate(versionId, attempts, incorrect));
        }
        Map<Long, DiscriminationAggregate> discrimination = new HashMap<>();
        for (Object[] row : versions.aggregateCurrentVersionDiscrimination()) {
            Long questionId = ((Number) row[0]).longValue();
            discrimination.put(questionId, new DiscriminationAggregate(
                    ((Number) row[2]).intValue(),
                    ((Number) row[3]).intValue(),
                    ((Number) row[4]).intValue(),
                    ((Number) row[5]).intValue()
            ));
        }
        Map<Long, Integer> openFlagCounts = new HashMap<>();
        questionFlags.findByStatus(QuestionFlagStatus.OPEN)
                .forEach(flag -> openFlagCounts.merge(flag.getQuestion().getId(), 1, Integer::sum));

        List<QuestionHealthItemResponse> items = questions.findAll().stream()
                .sorted((left, right) -> right.getUpdatedAt().compareTo(left.getUpdatedAt()))
                .map(question -> toHealthItem(
                        question,
                        aggregates.get(question.getId()),
                        discrimination.get(question.getId()),
                        openFlagCounts.getOrDefault(question.getId(), 0)
                ))
                .filter(item -> matchesQuality(item, quality))
                .toList();

        int withAttempts = 0;
        int highError = 0;
        int noAttempts = 0;
        int needsReview = 0;
        int flagged = 0;
        int weakDiscrimination = 0;
        for (QuestionHealthItemResponse item : items) {
            if (item.attempts() > 0) {
                withAttempts++;
            }
            if ("high_error".equals(item.qualitySignal())) {
                highError++;
            }
            if ("no_attempts".equals(item.qualitySignal())) {
                noAttempts++;
            }
            if ("needs_review".equals(item.status())) {
                needsReview++;
            }
            if (item.openFlagCount() > 0) {
                flagged++;
            }
            if ("weak_discrimination".equals(item.qualitySignal())) {
                weakDiscrimination++;
            }
        }

        return new QuestionHealthSummaryResponse(
                items.size(),
                withAttempts,
                highError,
                noAttempts,
                needsReview,
                flagged,
                weakDiscrimination,
                items
        );
    }

    @Transactional
    public QuestionResponse createQuestion(QuestionForm form) {
        validate(form);
        QuestionStatus status = initialStatus(form.getStatus());
        Question question = questions.save(new Question(status, currentUser()));
        QuestionVersion version = buildVersion(question, 1, form);
        versions.save(version);
        question.setCurrentVersion(version);
        audit.record("question_created", "Question", question.getId(), form.getType().name());
        return toResponse(question);
    }

    @Transactional
    public QuestionImportJobResponse importQuestions(QuestionImportRequest request) {
        List<QuestionForm> rows = request.getQuestions();
        if (rows.isEmpty()) {
            throw new QuestionBankException("question_import_empty");
        }
        QuestionImportJob job = importJobs.save(new QuestionImportJob(
                "JSON",
                rows.size(),
                null,
                toJson(request)
        ));
        return importQuestionRows(job, rows);
    }

    @Transactional
    public QuestionImportJobResponse importQuestionsFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new QuestionBankException("question_import_file_required");
        }
        List<QuestionForm> rows = parseExcel(file);
        if (rows.isEmpty()) {
            throw new QuestionBankException("question_import_empty");
        }
        QuestionImportJob job = importJobs.save(new QuestionImportJob(
                "XLSX",
                rows.size(),
                safeFilename(file.getOriginalFilename()),
                excelPayloadSummary(file, rows.size())
        ));
        return importQuestionRows(job, rows);
    }

    private QuestionImportJobResponse importQuestionRows(QuestionImportJob job, List<QuestionForm> rows) {
        List<QuestionImportErrorResponse> errors = new ArrayList<>();
        int imported = 0;

        for (int index = 0; index < rows.size(); index++) {
            QuestionForm row = rows.get(index);
            int rowNo = index + 1;
            try {
                row.setStatus(QuestionStatus.NEEDS_REVIEW);
                QuestionResponse created = createQuestion(row);
                imported++;
                audit.record("question_import_row_created", "Question", created.id(), "job=" + job.getId());
            } catch (QuestionBankException ex) {
                QuestionImportError error = importErrors.save(new QuestionImportError(
                        job,
                        rowNo,
                        ex.getCode(),
                        humanError(ex.getCode())
                ));
                errors.add(toImportErrorResponse(error));
            }
        }

        job.complete(imported, errors.size());
        audit.record("question_import_completed", "QuestionImportJob", job.getId(), job.getStatus().name());
        return toImportResponse(job, errors);
    }

    @Transactional(readOnly = true)
    public List<QuestionFlagResponse> listQuestionFlags(Long questionId) {
        return questionFlags.findByQuestionIdOrderByCreatedAtDesc(questionId).stream()
                .map(this::toFlagResponse)
                .toList();
    }

    @Transactional
    public QuestionFlagResponse createQuestionFlag(Long questionId, QuestionFlagSource source, String reason) {
        Question question = findQuestion(questionId);
        if (isBlank(reason)) {
            throw new QuestionBankException("question_flag_reason_required");
        }
        QuestionFlag flag = questionFlags.save(new QuestionFlag(question, source, reason.trim()));
        audit.record("question_flag_created", "Question", question.getId(), source.name());
        return toFlagResponse(flag);
    }

    @Transactional
    public QuestionResponse updateQuestion(Long id, QuestionForm form) {
        validate(form);
        Question question = findQuestion(id);
        QuestionVersion current = question.getCurrentVersion();
        if (current == null) {
            throw new QuestionBankException("question_version_not_found");
        }
        if (question.getStatus() == QuestionStatus.PUBLISHED) {
            QuestionVersion existingDraft = pendingDraft(question).orElse(null);
            if (existingDraft != null) {
                QuestionVersion replacement = buildVersion(question, existingDraft.getVersionNo(), form);
                existingDraft.replaceContent(replacement);
            } else {
                QuestionVersion replacement = buildVersion(question, nextVersionNo(question), form);
                versions.save(replacement);
            }
        } else if (question.getStatus() == QuestionStatus.APPROVED) {
            QuestionVersion replacement = buildVersion(question, nextVersionNo(question), form);
            versions.save(replacement);
            question.setCurrentVersion(replacement);
            question.changeStatus(QuestionStatus.NEEDS_REVIEW);
        } else {
            QuestionVersion replacement = buildVersion(question, nextVersionNo(question), form);
            current.replaceContent(replacement);
            question.changeStatus(initialStatus(form.getStatus()));
        }
        audit.record("question_updated", "Question", question.getId(), form.getType().name());
        return toResponse(question);
    }

    @Transactional
    public QuestionResponse approve(Long id) {
        Question question = findQuestion(id);
        requirePublishable(question);
        if (question.getStatus() != QuestionStatus.PUBLISHED) {
            question.changeStatus(QuestionStatus.APPROVED);
        }
        audit.record("question_approved", "Question", question.getId(), question.getCurrentVersion().getType().name());
        return toResponse(question);
    }

    @Transactional
    public QuestionResponse publish(Long id) {
        Question question = findQuestion(id);
        requirePublishable(question);
        QuestionVersion draft = pendingDraft(question).orElse(null);
        boolean hasDraft = draft != null;
        if (question.getStatus() != QuestionStatus.APPROVED
                && !(question.getStatus() == QuestionStatus.PUBLISHED && hasDraft)) {
            throw new QuestionBankException("question_not_approved");
        }
        if (hasDraft) {
            question.setCurrentVersion(draft);
            draft.markPublished();
        } else {
            question.getCurrentVersion().markPublished();
        }
        question.changeStatus(QuestionStatus.PUBLISHED);
        audit.record("question_published", "Question", question.getId(), question.getCurrentVersion().getType().name());
        return toResponse(question);
    }

    @Transactional
    public QuestionResponse archive(Long id) {
        Question question = findQuestion(id);
        question.changeStatus(QuestionStatus.ARCHIVED);
        audit.record("question_archived", "Question", question.getId(), question.getCurrentVersion().getType().name());
        return toResponse(question);
    }

    @Transactional
    public QuestionResponse flagForReview(Long id, String reason) {
        Question question = findQuestion(id);
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new QuestionBankException("question_archived");
        }
        if (!isBlank(reason)) {
            questionFlags.save(new QuestionFlag(question, QuestionFlagSource.ANALYTICS, reason.trim()));
        }
        question.changeStatus(QuestionStatus.NEEDS_REVIEW);
        audit.record("question_flagged_for_review", "Question", question.getId(), trimToNull(reason));
        return toResponse(question);
    }

    @Transactional(readOnly = true)
    public MiniLectureDraftResponse composeMiniLectureDraft(QuestionForm form) {
        if (form == null || form.getType() == null) {
            throw new QuestionBankException("question_type_required");
        }
        if (form.getTopicId() == null) {
            throw new QuestionBankException("topic_not_found");
        }
        Topic topic = findTopic(form.getTopicId());
        if (isBlank(form.getBodyRu()) || isBlank(form.getBodyKk())) {
            throw new QuestionBankException("question_body_required");
        }

        String correctAnswerRu = correctAnswerForLanguage(form, true);
        String correctAnswerKk = correctAnswerForLanguage(form, false);
        int difficulty = form.getDifficulty();
        if (difficulty < 1 || difficulty > 5) {
            difficulty = 2;
        }
        boolean math = isMathTopic(topic);
        String lectureRu = pedagogicalLectureForLanguage(true, topic, form.getType(), form.getBodyRu(), correctAnswerRu, difficulty, math);
        String lectureKk = pedagogicalLectureForLanguage(false, topic, form.getType(), form.getBodyKk(), correctAnswerKk, difficulty, math);

        return new MiniLectureDraftResponse(
                lectureRu,
                lectureKk,
                lectureRu,
                lectureKk,
                correctAnswerRu,
                correctAnswerKk
        );
    }

    private Specification<Question> filter(
            Long subjectId,
            Long gradeId,
            Long topicId,
            QuestionStatus status,
            QuestionType type,
            String query
    ) {
        return (root, criteriaQuery, cb) -> {
            Join<Question, QuestionVersion> version = root.join("currentVersion", JoinType.LEFT);
            java.util.ArrayList<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (subjectId != null) {
                predicates.add(cb.equal(version.get("topic").get("subject").get("id"), subjectId));
            }
            if (gradeId != null) {
                predicates.add(cb.equal(version.get("topic").get("grade").get("id"), gradeId));
            }
            if (topicId != null) {
                predicates.add(cb.equal(version.get("topic").get("id"), topicId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                predicates.add(cb.equal(version.get("type"), type));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.toLowerCase(Locale.ROOT).trim() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(version.get("bodyRu")), pattern),
                        cb.like(cb.lower(version.get("bodyKk")), pattern),
                        cb.like(cb.lower(version.get("source")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private QuestionForm toEditForm(Question question, QuestionVersion version) {
        QuestionForm form = new QuestionForm();
        form.setTopicId(version.getTopic().getId());
        form.setAtomicSkillId(version.getAtomicSkill() == null ? null : version.getAtomicSkill().getId());
        form.setType(version.getType());
        form.setDifficulty(version.getDifficulty());
        form.setBodyRu(version.getBodyRu());
        form.setBodyKk(version.getBodyKk());
        form.setSource(version.getSource());
        form.setExplanationRu(version.getExplanationRu());
        form.setExplanationKk(version.getExplanationKk());
        form.setMiniLectureRu(version.getMiniLectureRu());
        form.setMiniLectureKk(version.getMiniLectureKk());
        form.setStatus(question.getStatus() == QuestionStatus.NEEDS_REVIEW
                ? QuestionStatus.NEEDS_REVIEW
                : QuestionStatus.DRAFT);
        switch (version.getType()) {
            case SCQ, MCQ -> form.setOptions(editChoiceOptions(version));
            case MATCHING -> form.setMatchingPairs(editMatchingPairs(version));
            case FILL_IN -> form.setFillAnswers(editFillAnswers(version));
        }
        ensureChoiceRows(form.getOptions(), 4);
        ensureMatchingRows(form.getMatchingPairs(), 2);
        ensureFillRows(form.getFillAnswers(), Math.max(1, form.getFillAnswers().size()));
        if (isBlank(form.getMiniLectureRu()) && !isBlank(version.getExplanationRu())) {
            form.setMiniLectureRu(version.getExplanationRu());
        }
        if (isBlank(form.getMiniLectureKk()) && !isBlank(version.getExplanationKk())) {
            form.setMiniLectureKk(version.getExplanationKk());
        }
        return form;
    }

    private List<ChoiceOptionForm> editChoiceOptions(QuestionVersion version) {
        List<ChoiceOptionForm> result = new ArrayList<>();
        JsonNode options = readTree(version.getOptionsJson());
        List<String> correct = readCorrectLabels(version.getAnswerKeyJson());
        for (JsonNode option : options) {
            String label = trimToNull(option.path("label").asText());
            result.add(new ChoiceOptionForm(
                    label == null ? String.valueOf((char) ('A' + result.size())) : label,
                    option.path("textRu").asText(""),
                    option.path("textKk").asText(""),
                    label != null && correct.contains(label.toUpperCase(Locale.ROOT))
            ));
        }
        return result;
    }

    private List<String> readCorrectLabels(String answerKeyJson) {
        List<String> labels = new ArrayList<>();
        JsonNode key = readTree(answerKeyJson);
        if (key.isArray()) {
            key.forEach(item -> labels.add(item.asText("").trim().toUpperCase(Locale.ROOT)));
        }
        return labels;
    }

    private List<MatchingPairForm> editMatchingPairs(QuestionVersion version) {
        List<MatchingPairForm> result = new ArrayList<>();
        JsonNode pairs = readTree(version.getOptionsJson());
        for (JsonNode pair : pairs) {
            result.add(new MatchingPairForm(
                    pair.path("leftRu").asText(""),
                    pair.path("leftKk").asText(""),
                    pair.path("rightRu").asText(""),
                    pair.path("rightKk").asText("")
            ));
        }
        return result;
    }

    private List<FillAnswerForm> editFillAnswers(QuestionVersion version) {
        List<FillAnswerForm> result = new ArrayList<>();
        JsonNode key = readTree(version.getAnswerKeyJson());
        if (!key.isObject()) {
            return result;
        }
        key.fields().forEachRemaining(entry -> {
            JsonNode rule = entry.getValue();
            FillMatchMode mode = FillMatchMode.EXACT;
            String rawMode = trimToNull(rule.path("mode").asText());
            if (rawMode != null) {
                try {
                    mode = FillMatchMode.valueOf(rawMode.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    mode = FillMatchMode.EXACT;
                }
            }
            BigDecimal tolerance = null;
            String rawTolerance = trimToNull(rule.path("tolerance").asText());
            if (rawTolerance != null) {
                try {
                    tolerance = new BigDecimal(rawTolerance);
                } catch (NumberFormatException ignored) {
                    tolerance = null;
                }
            }
            result.add(new FillAnswerForm(
                    entry.getKey(),
                    rule.path("answer").asText(""),
                    mode,
                    tolerance
            ));
        });
        return result;
    }

    private void ensureChoiceRows(List<ChoiceOptionForm> options, int minRows) {
        while (options.size() < minRows) {
            char label = (char) ('A' + options.size());
            options.add(new ChoiceOptionForm(String.valueOf(label), "", "", false));
        }
    }

    private void ensureMatchingRows(List<MatchingPairForm> pairs, int minRows) {
        while (pairs.size() < minRows) {
            pairs.add(new MatchingPairForm("", "", "", ""));
        }
    }

    private void ensureFillRows(List<FillAnswerForm> answers, int minRows) {
        int target = Math.max(1, minRows);
        while (answers.size() < target) {
            answers.add(new FillAnswerForm("", "", FillMatchMode.EXACT, null));
        }
    }

    private void validate(QuestionForm form) {
        if (form.getType() == null) {
            throw new QuestionBankException("question_type_required");
        }
        Topic topic = findTopic(form.getTopicId());
        if (form.getAtomicSkillId() != null) {
            AtomicSkill skill = findSkill(form.getAtomicSkillId());
            if (!Objects.equals(skill.getTopic().getId(), topic.getId())) {
                throw new QuestionBankException("skill_topic_mismatch");
            }
        }
        if (isBlank(form.getBodyRu()) || isBlank(form.getBodyKk())) {
            throw new QuestionBankException("question_body_required");
        }
        if (isBlank(form.getSource())) {
            throw new QuestionBankException("question_source_required");
        }
        if (form.getDifficulty() < 1 || form.getDifficulty() > 5) {
            throw new QuestionBankException("question_difficulty_invalid");
        }
        switch (form.getType()) {
            case SCQ -> validateScq(form.getOptions());
            case MCQ -> validateMcq(form.getOptions());
            case MATCHING -> validateMatching(form.getMatchingPairs());
            case FILL_IN -> validateFillIn(form.getFillAnswers());
        }
    }

    private void validateScq(List<ChoiceOptionForm> options) {
        List<ChoiceOptionForm> activeOptions = activeChoiceOptions(options);
        validateChoiceOptions(activeOptions);
        long correct = activeOptions.stream().filter(ChoiceOptionForm::isCorrect).count();
        if (correct != 1) {
            throw new QuestionBankException("scq_requires_exactly_one_correct");
        }
    }

    private void validateMcq(List<ChoiceOptionForm> options) {
        List<ChoiceOptionForm> activeOptions = activeChoiceOptions(options);
        validateChoiceOptions(activeOptions);
        long correct = activeOptions.stream().filter(ChoiceOptionForm::isCorrect).count();
        if (correct < 1) {
            throw new QuestionBankException("mcq_requires_one_correct");
        }
    }

    private void validateChoiceOptions(List<ChoiceOptionForm> options) {
        List<ChoiceOptionForm> filled = options.stream()
                .filter(option -> !isBlank(option.getTextRu()) || !isBlank(option.getTextKk()))
                .toList();
        if (filled.size() < 2) {
            throw new QuestionBankException("choice_requires_two_options");
        }
        if (filled.stream().anyMatch(option -> isBlank(option.getTextRu()) || isBlank(option.getTextKk()))) {
            throw new QuestionBankException("choice_option_text_required");
        }
    }

    private List<ChoiceOptionForm> activeChoiceOptions(List<ChoiceOptionForm> options) {
        return options.stream()
                .filter(option -> !option.isSoftDeleted())
                .toList();
    }

    private void validateMatching(List<MatchingPairForm> pairs) {
        List<MatchingPairForm> filled = pairs.stream()
                .filter(pair -> !isBlank(pair.getLeftRu()) || !isBlank(pair.getRightRu())
                        || !isBlank(pair.getLeftKk()) || !isBlank(pair.getRightKk()))
                .toList();
        if (filled.size() < 2) {
            throw new QuestionBankException("matching_requires_two_pairs");
        }
        if (filled.stream().anyMatch(pair -> isBlank(pair.getLeftRu()) || isBlank(pair.getRightRu())
                || isBlank(pair.getLeftKk()) || isBlank(pair.getRightKk()))) {
            throw new QuestionBankException("matching_pair_required");
        }
    }

    private void validateFillIn(List<FillAnswerForm> answers) {
        List<FillAnswerForm> filled = answers.stream()
                .filter(answer -> !isBlank(answer.getPlaceholder()) || !isBlank(answer.getAnswer()))
                .toList();
        if (filled.isEmpty()) {
            throw new QuestionBankException("fill_in_requires_answer");
        }
        for (FillAnswerForm answer : filled) {
            if (isBlank(answer.getPlaceholder()) || isBlank(answer.getAnswer()) || answer.getMatchMode() == null) {
                throw new QuestionBankException("fill_in_answer_required");
            }
            if (answer.getMatchMode() == FillMatchMode.NUMERIC_TOLERANCE
                    && (answer.getTolerance() == null || answer.getTolerance().compareTo(BigDecimal.ZERO) < 0)) {
                throw new QuestionBankException("fill_in_tolerance_required");
            }
        }
    }

    private QuestionVersion buildVersion(Question question, int versionNo, QuestionForm form) {
        Topic topic = findTopic(form.getTopicId());
        AtomicSkill skill = form.getAtomicSkillId() == null ? null : findSkill(form.getAtomicSkillId());
        String studentFacingRu = coalesceStudentText(form.getMiniLectureRu(), form.getExplanationRu());
        String studentFacingKk = coalesceStudentText(form.getMiniLectureKk(), form.getExplanationKk());
        return new QuestionVersion(
                question,
                versionNo,
                form.getType(),
                topic,
                skill,
                form.getDifficulty(),
                form.getBodyRu().trim(),
                form.getBodyKk().trim(),
                studentFacingRu,
                studentFacingKk,
                studentFacingRu,
                studentFacingKk,
                form.getSource().trim(),
                optionsJson(form),
                answerKeyJson(form)
        );
    }

    private String correctAnswerForLanguage(QuestionForm form, boolean russian) {
        return switch (form.getType()) {
            case SCQ, MCQ -> {
                List<String> values = activeChoiceOptions(form.getOptions()).stream()
                        .filter(option -> !isBlank(option.getTextRu()) || !isBlank(option.getTextKk()))
                        .filter(ChoiceOptionForm::isCorrect)
                        .map(option -> {
                            String label = label(option);
                            String text = russian ? option.getTextRu() : option.getTextKk();
                            return label + " — " + text.trim();
                        })
                        .toList();
                if (values.isEmpty()) {
                    throw new QuestionBankException("question_correct_answer_required");
                }
                yield String.join("; ", values);
            }
            case MATCHING -> {
                List<MatchingPairForm> pairs = form.getMatchingPairs().stream()
                        .filter(pair -> !isBlank(pair.getLeftRu()) || !isBlank(pair.getRightRu())
                                || !isBlank(pair.getLeftKk()) || !isBlank(pair.getRightKk()))
                        .toList();
                if (pairs.isEmpty()) {
                    throw new QuestionBankException("question_correct_answer_required");
                }
                StringJoiner joiner = new StringJoiner("; ");
                int index = 1;
                for (MatchingPairForm pair : pairs) {
                    String left = russian ? pair.getLeftRu() : pair.getLeftKk();
                    String right = russian ? pair.getRightRu() : pair.getRightKk();
                    if (isBlank(left) || isBlank(right)) {
                        throw new QuestionBankException("matching_pair_required");
                    }
                    joiner.add(index + ") " + left.trim() + " -> " + right.trim());
                    index++;
                }
                yield joiner.toString();
            }
            case FILL_IN -> {
                List<FillAnswerForm> answers = form.getFillAnswers().stream()
                        .filter(answer -> !isBlank(answer.getPlaceholder()) && !isBlank(answer.getAnswer()))
                        .toList();
                if (answers.isEmpty()) {
                    throw new QuestionBankException("question_correct_answer_required");
                }
                StringJoiner joiner = new StringJoiner("; ");
                for (FillAnswerForm answer : answers) {
                    joiner.add(answer.getPlaceholder().trim() + " = " + answer.getAnswer().trim());
                }
                yield joiner.toString();
            }
        };
    }

    private String coalesceStudentText(String primary, String fallback) {
        String first = trimToNull(primary);
        if (first != null) {
            return first;
        }
        return trimToNull(fallback);
    }

    /**
     * Единый текст для ученика: контекст темы/предмета/класса, разбор с верным ответом и короткое закрепление.
     * Используется и как «объяснение», и как «мини-лекция» (одинаковое содержание).
     */
    private String pedagogicalLectureForLanguage(
            boolean russian,
            Topic topic,
            QuestionType type,
            String body,
            String correctAnswer,
            int difficulty,
            boolean math
    ) {
        String subject = russian
                ? nullToEmpty(topic.getSubject().getTitleRu())
                : nullToEmpty(topic.getSubject().getTitleKk());
        String grade = russian
                ? nullToEmpty(topic.getGrade().getTitleRu())
                : nullToEmpty(topic.getGrade().getTitleKk());
        String topicTitle = russian ? topic.getTitleRu() : topic.getTitleKk();
        String trimmedBody = body == null ? "" : body.trim();

        String typeHint = switch (type) {
            case SCQ -> russian
                    ? "Это задание с одним верным вариантом: нужно выбрать единственный ответ, который точно следует из условия."
                    : "Бұл бір дұрыс нұсқасы бар тапсырма: шарттан дәл шығатын біреуін таңдау керек.";
            case MCQ -> russian
                    ? "Здесь может быть несколько верных вариантов: отметьте все, которые одновременно выполняют условие задачи."
                    : "Мұнда бірнеше дұрыс нұсқа болуы мүмкін: шартты бір мезгілде орындайтын барлық нұсқаларды белгілеңіз.";
            case MATCHING -> russian
                    ? "Нужно сопоставить пары так, чтобы смысл и формулы согласовались между левой и правой колонкой."
                    : "Сол және оң бағандардағы мағына мен формулалар сәйкес келетін жұптарды қосу керек.";
            case FILL_IN -> russian
                    ? "В каждый пропуск вставьте значение, которое проходит проверку по правилу (точное совпадение, нормализация, числовой допуск или регулярное выражение — как задано в ключе)."
                    : "Әр бос орынға тексеру ережесіне сай мәнді қойыңыз (дәл сәйкестік, нормализация, сандық қателік немесе регекс — кілтте қалай көрсетілсе).";
        };

        String breakdown = switch (type) {
            case SCQ, MCQ -> russian
                    ? "Сопоставьте условие с каждым вариантом: отметьте те, что следуют из данных задачи и не противоречат определениям темы «"
                            + topicTitle + "». Сверьте выбранное с ключом: верно — «" + correctAnswer + "»."
                    : "Әр нұсқаны шартпен салыстырыңыз: «" + topicTitle
                            + "» тақырыбының анықтамаларына қайшы келмейтіндерді таңдаңыз. Кілтпен тексеріңіз: дұрысы — «"
                            + correctAnswer + "».";
            case MATCHING -> russian
                    ? "Пройдите пары по порядку: для каждой левой части найдите правую по смыслу. Итоговое соответствие в ключе: "
                            + correctAnswer + "."
                    : "Жұптарды ретпен қараңыз: әр сол жақ үшін мағынасы сай оң жақты табыңыз. Кілттегі сәйкестік: "
                            + correctAnswer + ".";
            case FILL_IN -> russian
                    ? "Подставьте в пропуски значения из ключа и проверьте по правилу каждого placeholder. Верные значения: "
                            + correctAnswer + "."
                    : "Бос орындарға кілттегі мәндерді қойыңыз және әр placeholder үшін ережеге сәйкестігін тексеріңіз. Дұрыс мәндер: "
                            + correctAnswer + ".";
        };

        String reinforcement = math
                ? (russian
                ? "Для закрепления придумайте похожий пример с другими числами по той же теме и проверьте ответ тем же способом, что и в разборе выше."
                : "Нығайту үшін сол тақырып бойынша басқа сандармен ұқсас мысал ойлап, жоғарыдағы тәсілмен жауапты тексеріңіз.")
                : (russian
                ? "Для закрепления сформулируйте короткий аналогичный вопрос по теме «" + topicTitle
                        + "» и проверьте ответ, шаг за шагом повторяя ход из разбора."
                : "Нығайту үшін «" + topicTitle + "» тақырыбындағы қысқа ұқсас сұрақ құрастырып, жауапты жоғарыдағы тәсілмен тексеріңіз.");

        if (russian) {
            StringBuilder b = new StringBuilder();
            b.append("Объяснение для школьника\n");
            b.append("Предмет: ").append(subject).append(". Класс: ").append(grade).append(". Тема: ").append(topicTitle).append(".\n");
            b.append("Сложность в банке вопросов: ").append(difficulty).append(" из 5.\n\n");
            b.append("Текст задания:\n«").append(trimmedBody).append("»\n\n");
            b.append("Как устроен этот тип задания. ").append(typeHint).append("\n\n");
            b.append("Разбор и верный ответ\n");
            b.append(breakdown).append("\n\n");
            b.append("Кратко: верно — ").append(correctAnswer).append(".\n\n");
            b.append("Ещё пример на закрепление\n");
            b.append(reinforcement);
            return b.toString();
        }

        StringBuilder b = new StringBuilder();
        b.append("Мектеп оқушысына түсіндірме\n");
        b.append("Пән: ").append(subject).append(". Сынып: ").append(grade).append(". Тақырып: ").append(topicTitle).append(".\n");
        b.append("Сұрақ қиындығы (банк): ").append(difficulty).append(" / 5.\n\n");
        b.append("Тапсырма мәтіні:\n«").append(trimmedBody).append("»\n\n");
        b.append("Тапсырма түрі. ").append(typeHint).append("\n\n");
        b.append("Талдау және дұрыс жауап\n");
        b.append(breakdown).append("\n\n");
        b.append("Қысқаша: дұрыс жауап — ").append(correctAnswer).append(".\n\n");
        b.append("Нығайтуға қосымша мысал\n");
        b.append(reinforcement);
        return b.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isMathTopic(Topic topic) {
        String code = topic.getSubject().getCode() == null ? "" : topic.getSubject().getCode().toLowerCase(Locale.ROOT);
        String ru = topic.getSubject().getTitleRu() == null ? "" : topic.getSubject().getTitleRu().toLowerCase(Locale.ROOT);
        String kk = topic.getSubject().getTitleKk() == null ? "" : topic.getSubject().getTitleKk().toLowerCase(Locale.ROOT);
        return code.contains("math")
                || ru.contains("матем")
                || ru.contains("алгебр")
                || ru.contains("геометр")
                || kk.contains("матем")
                || kk.contains("алгебр")
                || kk.contains("геометр");
    }

    private String optionsJson(QuestionForm form) {
        Object value = switch (form.getType()) {
            case SCQ, MCQ -> activeChoiceOptions(form.getOptions()).stream()
                    .filter(option -> !isBlank(option.getTextRu()) || !isBlank(option.getTextKk()))
                    .map(option -> Map.of(
                            "label", label(option),
                            "textRu", option.getTextRu().trim(),
                            "textKk", option.getTextKk().trim()
                    ))
                    .toList();
            case MATCHING -> form.getMatchingPairs().stream()
                    .filter(pair -> !isBlank(pair.getLeftRu()) || !isBlank(pair.getRightRu()))
                    .map(pair -> Map.of(
                            "leftRu", pair.getLeftRu().trim(),
                            "leftKk", pair.getLeftKk().trim(),
                            "rightRu", pair.getRightRu().trim(),
                            "rightKk", pair.getRightKk().trim()
                    ))
                    .toList();
            case FILL_IN -> List.of();
        };
        return toJson(value);
    }

    private String answerKeyJson(QuestionForm form) {
        Object value = switch (form.getType()) {
            case SCQ, MCQ -> activeChoiceOptions(form.getOptions()).stream()
                    .filter(option -> !isBlank(option.getTextRu()) || !isBlank(option.getTextKk()))
                    .filter(ChoiceOptionForm::isCorrect)
                    .map(this::label)
                    .toList();
            case MATCHING -> {
                List<Map<String, String>> pairs = form.getMatchingPairs().stream()
                        .filter(pair -> !isBlank(pair.getLeftRu()) || !isBlank(pair.getRightRu()))
                        .map(pair -> Map.of("left", pair.getLeftRu().trim(), "right", pair.getRightRu().trim()))
                        .toList();
                yield Map.of("pairs", pairs);
            }
            case FILL_IN -> {
                Map<String, Object> answers = new LinkedHashMap<>();
                form.getFillAnswers().stream()
                        .filter(answer -> !isBlank(answer.getPlaceholder()) || !isBlank(answer.getAnswer()))
                        .forEach(answer -> answers.put(answer.getPlaceholder().trim(), Map.of(
                                "answer", answer.getAnswer().trim(),
                                "mode", answer.getMatchMode().name(),
                                "tolerance", answer.getTolerance() == null ? "" : answer.getTolerance().toPlainString()
                        )));
                yield answers;
            }
        };
        return toJson(value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new QuestionBankException("question_payload_invalid");
        }
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new QuestionBankException("question_payload_invalid");
        }
    }

    private QuestionStatus initialStatus(QuestionStatus requested) {
        if (requested == QuestionStatus.NEEDS_REVIEW) {
            return QuestionStatus.NEEDS_REVIEW;
        }
        return QuestionStatus.DRAFT;
    }

    private int nextVersionNo(Question question) {
        return versions.findMaxVersionNoByQuestionId(question.getId()) + 1;
    }

    private void requirePublishable(Question question) {
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new QuestionBankException("question_archived");
        }
        if (question.getCurrentVersion() == null) {
            throw new QuestionBankException("question_version_not_found");
        }
    }

    private QuestionResponse toResponse(Question question) {
        QuestionVersion version = question.getCurrentVersion();
        Integer pendingVersionNo = version == null
                ? null
                : pendingDraft(question).map(QuestionVersion::getVersionNo).orElse(null);
        return new QuestionResponse(
                question.getId(),
                question.getStatus().apiValue(),
                version == null ? null : version.getId(),
                version == null ? 0 : version.getVersionNo(),
                version == null ? null : version.getType().name(),
                version == null ? null : version.getTopic().getId(),
                version == null ? null : version.getTopic().getTitleRu(),
                version == null || version.getAtomicSkill() == null ? null : version.getAtomicSkill().getId(),
                version == null || version.getAtomicSkill() == null ? null : version.getAtomicSkill().getTitleRu(),
                version == null ? 0 : version.getDifficulty(),
                version == null ? null : version.getBodyRu(),
                version == null ? null : version.getBodyKk(),
                version == null ? null : version.getSource(),
                question.getUpdatedAt(),
                pendingVersionNo
        );
    }

    private QuestionHealthItemResponse toHealthItem(
            Question question,
            HealthAggregate aggregate,
            DiscriminationAggregate discrimination,
            int openFlagCount
    ) {
        QuestionVersion version = question.getCurrentVersion();
        int attempts = aggregate == null ? 0 : aggregate.attempts();
        int incorrect = aggregate == null ? 0 : aggregate.incorrectAnswers();
        int errorRate = attempts == 0 ? 0 : Math.round((incorrect * 100.0f) / attempts);
        int discriminativePower = discrimination == null ? 0 : discrimination.power();
        String qualitySignal = qualitySignal(question, attempts, errorRate, discriminativePower, openFlagCount);
        return new QuestionHealthItemResponse(
                question.getId(),
                version == null ? null : version.getId(),
                question.getStatus().apiValue(),
                version == null ? null : version.getType().name(),
                version == null ? null : version.getTopic().getTitleRu(),
                version == null ? null : version.getBodyRu(),
                attempts,
                incorrect,
                errorRate,
                discriminativePower,
                openFlagCount,
                qualitySignal
        );
    }

    private boolean matchesQuality(QuestionHealthItemResponse item, QuestionQualityFilter quality) {
        if (quality == null) {
            return true;
        }
        return switch (quality) {
            case HIGH_ERROR -> "high_error".equals(item.qualitySignal());
            case NO_ATTEMPTS -> "no_attempts".equals(item.qualitySignal());
            case NEEDS_REVIEW -> "needs_review".equals(item.status());
            case FLAGGED -> item.openFlagCount() > 0;
            case WEAK_DISCRIMINATION -> "weak_discrimination".equals(item.qualitySignal());
        };
    }

    private String qualitySignal(Question question, int attempts, int errorRate, int discriminativePower, int openFlagCount) {
        if (question.getStatus() == QuestionStatus.NEEDS_REVIEW) {
            return "needs_review";
        }
        if (openFlagCount > 0) {
            return "flagged";
        }
        if (attempts == 0) {
            return "no_attempts";
        }
        if (attempts >= 3 && errorRate >= 70) {
            return "high_error";
        }
        if (attempts >= 6 && discriminativePower < 10) {
            return "weak_discrimination";
        }
        return "ok";
    }

    private QuestionImportJobResponse toImportResponse(
            QuestionImportJob job,
            List<QuestionImportErrorResponse> errors
    ) {
        return new QuestionImportJobResponse(
                job.getId(),
                job.getStatus().name().toLowerCase(Locale.ROOT),
                job.getSourceType(),
                job.getOriginalFilename(),
                job.getTotalRows(),
                job.getImportedRows(),
                job.getErrorRows(),
                job.getCreatedAt(),
                errors
        );
    }

    private QuestionImportErrorResponse toImportErrorResponse(QuestionImportError error) {
        return new QuestionImportErrorResponse(error.getRowNo(), error.getErrorCode(), error.getMessage());
    }

    private QuestionFlagResponse toFlagResponse(QuestionFlag flag) {
        QuestionVersion version = flag.getQuestionVersion();
        return new QuestionFlagResponse(
                flag.getId(),
                flag.getQuestion().getId(),
                version == null ? null : version.getId(),
                flag.getSource().name().toLowerCase(Locale.ROOT),
                flag.getStatus().name().toLowerCase(Locale.ROOT),
                flag.getReason(),
                flag.getCreatedAt(),
                flag.getResolvedAt()
        );
    }

    private List<QuestionForm> parseExcel(MultipartFile file) {
        try (InputStream input = file.getInputStream(); Workbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<QuestionForm> rows = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlank(cell(row, 0, formatter))) {
                    continue;
                }
                rows.add(formFromExcelRow(row, formatter));
            }
            return rows;
        } catch (IOException | IllegalArgumentException ex) {
            throw new QuestionBankException("question_import_excel_invalid");
        }
    }

    private QuestionForm formFromExcelRow(Row row, DataFormatter formatter) {
        QuestionForm form = new QuestionForm();
        form.setType(QuestionType.valueOf(cell(row, 0, formatter).trim().toUpperCase(Locale.ROOT)));
        form.setTopicId(Long.valueOf(cell(row, 1, formatter).trim()));
        form.setDifficulty(Integer.parseInt(cell(row, 2, formatter).trim()));
        form.setBodyRu(cell(row, 3, formatter));
        form.setBodyKk(cell(row, 4, formatter));
        form.setSource(cell(row, 5, formatter));
        form.setExplanationRu(trimToNull(cell(row, 8, formatter)));
        form.setExplanationKk(trimToNull(cell(row, 9, formatter)));
        switch (form.getType()) {
            case SCQ, MCQ -> {
                form.setOptions(parseChoiceOptions(cell(row, 6, formatter), cell(row, 7, formatter)));
            }
            case MATCHING -> form.setMatchingPairs(parseMatchingPairs(cell(row, 6, formatter)));
            case FILL_IN -> form.setFillAnswers(parseFillAnswers(cell(row, 6, formatter)));
        }
        return form;
    }

    private List<ChoiceOptionForm> parseChoiceOptions(String rawOptions, String rawCorrect) {
        List<String> correctLabels = split(rawCorrect, ",").stream()
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .toList();
        List<ChoiceOptionForm> options = new ArrayList<>();
        for (String chunk : split(rawOptions, ";")) {
            List<String> parts = split(chunk, "\\|");
            if (parts.size() >= 3) {
                String label = parts.get(0).trim().toUpperCase(Locale.ROOT);
                options.add(new ChoiceOptionForm(label, parts.get(1).trim(), parts.get(2).trim(), correctLabels.contains(label)));
            }
        }
        return options;
    }

    private List<MatchingPairForm> parseMatchingPairs(String rawPairs) {
        List<MatchingPairForm> pairs = new ArrayList<>();
        for (String chunk : split(rawPairs, ";")) {
            List<String> parts = split(chunk, "\\|");
            if (parts.size() >= 4) {
                pairs.add(new MatchingPairForm(parts.get(0).trim(), parts.get(1).trim(), parts.get(2).trim(), parts.get(3).trim()));
            }
        }
        return pairs;
    }

    private List<FillAnswerForm> parseFillAnswers(String rawAnswers) {
        List<FillAnswerForm> answers = new ArrayList<>();
        for (String chunk : split(rawAnswers, ";")) {
            List<String> parts = split(chunk, "\\|");
            if (parts.size() >= 3) {
                BigDecimal tolerance = parts.size() >= 4 && !parts.get(3).isBlank() ? new BigDecimal(parts.get(3).trim()) : null;
                answers.add(new FillAnswerForm(
                        parts.get(0).trim(),
                        parts.get(1).trim(),
                        FillMatchMode.valueOf(parts.get(2).trim().toUpperCase(Locale.ROOT)),
                        tolerance
                ));
            }
        }
        return answers;
    }

    private List<String> split(String value, String delimiterRegex) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(delimiterRegex))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String cell(Row row, int index, DataFormatter formatter) {
        Cell cell = row.getCell(index);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String cleaned = filename.replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        return slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
    }

    private String excelPayloadSummary(MultipartFile file, int rowCount) {
        return "filename=" + safeFilename(file.getOriginalFilename()) + "; size=" + file.getSize() + "; rows=" + rowCount;
    }

    private Question findQuestion(Long id) {
        return questions.findById(id).orElseThrow(() -> new QuestionBankException("question_not_found"));
    }

    private Topic findTopic(Long id) {
        return topics.findById(id).orElseThrow(() -> new QuestionBankException("topic_not_found"));
    }

    private AtomicSkill findSkill(Long id) {
        return skills.findById(id).orElseThrow(() -> new QuestionBankException("skill_not_found"));
    }

    private AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return users.findByEmailIgnoreCase(authentication.getName()).orElse(null);
    }

    private String label(ChoiceOptionForm option) {
        return isBlank(option.getLabel()) ? "?" : option.getLabel().trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String humanError(String code) {
        return switch (code) {
            case "scq_requires_exactly_one_correct" -> "Для SCQ нужен ровно один правильный ответ";
            case "mcq_requires_one_correct" -> "Для MCQ нужен минимум один правильный ответ";
            case "choice_requires_two_options" -> "Добавьте минимум два варианта ответа";
            case "choice_option_text_required" -> "Заполните RU и KK текст каждого варианта";
            case "matching_requires_two_pairs" -> "Для сопоставления нужны минимум две пары";
            case "matching_pair_required" -> "Заполните обе стороны пары RU и KK";
            case "fill_in_requires_answer" -> "Для FILL_IN нужен ключ ответа";
            case "fill_in_answer_required" -> "Заполните placeholder, ответ и правило проверки";
            case "fill_in_tolerance_required" -> "Для числового допуска укажите неотрицательный tolerance";
            case "question_correct_answer_required" -> "Сначала задайте корректный правильный ответ для выбранного типа";
            case "skill_topic_mismatch" -> "Атомарный навык должен принадлежать выбранной теме";
            case "question_source_required" -> "Источник вопроса обязателен";
            case "topic_not_found" -> "Тема не найдена";
            case "skill_not_found" -> "Навык не найден";
            case "question_import_excel_invalid" -> "Excel-файл не прочитан";
            case "question_import_file_required" -> "Выберите Excel-файл";
            case "question_flag_reason_required" -> "Укажите причину флага";
            default -> code;
        };
    }

    private record HealthAggregate(Long versionId, int attempts, int incorrectAnswers) {
    }

    private record DiscriminationAggregate(int highTotal, int highCorrect, int lowTotal, int lowCorrect) {
        int power() {
            if (highTotal == 0 || lowTotal == 0) {
                return 0;
            }
            int highRate = Math.round((highCorrect * 100.0f) / highTotal);
            int lowRate = Math.round((lowCorrect * 100.0f) / lowTotal);
            return highRate - lowRate;
        }
    }
}
