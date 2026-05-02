package kz.damulab.lectures;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import kz.damulab.audit.AdminContentAuditService;
import kz.damulab.content.Topic;
import kz.damulab.content.TopicRepository;
import kz.damulab.questions.QuestionStatus;
import kz.damulab.questions.QuestionVersion;
import kz.damulab.questions.QuestionVersionRepository;
import kz.damulab.users.AppUser;
import kz.damulab.users.AppUserRepository;

@Service
public class LectureService {

    private static final int DEFAULT_AUTO_CHECKPOINT_COUNT = 3;
    private static final int MAX_ATTACHMENTS = 8;
    private static final int MAX_FORMULA_LENGTH = 1000;
    private static final String SAFE_BASE_URI = "https://damulab.local/";
    private static final Pattern UNSAFE_FORMULA_PATTERN = Pattern.compile(
            "(?i)\\\\(?:htmlClass|htmlId|htmlStyle|htmlData|href|url|includegraphics)\\b");
    private static final Set<String> ALLOWED_ATTACHMENT_TYPES = Set.of("link", "pdf", "video", "image");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".webm", ".ogg", ".mov");
    private static final Safelist LECTURE_SAFE_HTML = Safelist.none()
            .addTags("p", "br", "strong", "b", "em", "i", "u", "s", "blockquote", "pre", "code", "ul", "ol", "li",
                    "h1", "h2", "h3", "h4", "h5", "h6", "a", "span", "div", "sub", "sup", "hr",
                    "table", "thead", "tbody", "tr", "th", "td")
            .addAttributes(":all", "class")
            .addAttributes("a", "href", "target", "rel")
            .addAttributes("span", "data-value", "contenteditable")
            .addAttributes("th", "colspan", "rowspan")
            .addAttributes("td", "colspan", "rowspan")
            .addProtocols("a", "href", "http", "https", "mailto")
            .preserveRelativeLinks(true);

    private final LectureRepository lectures;
    private final LectureVersionRepository versions;
    private final LectureAttachmentRepository attachments;
    private final LectureCheckpointRepository checkpoints;
    private final TopicRepository topics;
    private final QuestionVersionRepository questionVersions;
    private final AppUserRepository users;
    private final AdminContentAuditService audit;
    private final LectureAttachmentStorageService attachmentStorage;

    public LectureService(
            LectureRepository lectures,
            LectureVersionRepository versions,
            LectureAttachmentRepository attachments,
            LectureCheckpointRepository checkpoints,
            TopicRepository topics,
            QuestionVersionRepository questionVersions,
            AppUserRepository users,
            AdminContentAuditService audit,
            LectureAttachmentStorageService attachmentStorage
    ) {
        this.lectures = lectures;
        this.versions = versions;
        this.attachments = attachments;
        this.checkpoints = checkpoints;
        this.topics = topics;
        this.questionVersions = questionVersions;
        this.users = users;
        this.audit = audit;
        this.attachmentStorage = attachmentStorage;
    }

    @Transactional(readOnly = true)
    public List<LectureResponse> listLectures(Long topicId, LectureStatus status, String query) {
        return lectures.findAll(filter(topicId, status, query)).stream()
                .sorted((left, right) -> right.getUpdatedAt().compareTo(left.getUpdatedAt()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LectureResponse> listPublishedLectures() {
        return lectures.findByStatusOrderByUpdatedAtDesc(LectureStatus.PUBLISHED).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LectureResponse getLecture(Long id) {
        return toResponse(findLecture(id));
    }

    @Transactional(readOnly = true)
    public LectureResponse getPublishedLecture(Long id) {
        Lecture lecture = findLecture(id);
        if (lecture.getStatus() != LectureStatus.PUBLISHED) {
            throw new LectureException("lecture_not_found");
        }
        return toResponse(lecture);
    }

    @Transactional(readOnly = true)
    public LectureForm toEditForm(Long id) {
        LectureVersion version = requireCurrentVersion(findLecture(id));
        LectureForm form = new LectureForm();
        form.setTopicId(version.getTopic() == null ? null : version.getTopic().getId());
        form.setTitleRu(version.getTitleRu());
        form.setTitleKk(version.getTitleKk());
        form.setContentRu(editableContent(version.getContentRuHtml()));
        form.setContentKk(editableContent(version.getContentKkHtml()));
        form.setSource(version.getSource());
        form.setControlMode(version.getControlMode());
        form.setAutoCheckpointCount(version.getCheckpointCountRequested());
        form.setAttachments(attachments.findByLectureVersionIdOrderBySortOrderAscIdAsc(version.getId()).stream()
                .map(item -> new LectureAttachmentForm(
                        item.getTitle(),
                        item.getUrl(),
                        item.getMediaType(),
                        item.getStorageKey(),
                        item.getOriginalFileName(),
                        item.getFileContentType(),
                        item.getFileSizeBytes()
                ))
                .toList());
        form.setCheckpointQuestionVersionIds(checkpoints.findByLectureVersionIdOrderBySortOrderAscIdAsc(version.getId()).stream()
                .map(item -> item.getQuestionVersion().getId())
                .toList());
        return form;
    }

    @Transactional
    public LectureResponse createLecture(LectureForm form) {
        return createLecture(form, List.of());
    }

    @Transactional
    public LectureResponse createLecture(LectureForm form, List<MultipartFile> attachmentFiles) {
        List<PreparedAttachment> preparedAttachments = prepareAttachments(form.getAttachments(), attachmentFiles);
        try {
            validateDraft(form, preparedAttachments);
            Lecture lecture = lectures.save(new Lecture(LectureStatus.DRAFT, currentUser()));
            LectureVersion version = versions.save(buildVersion(lecture, 1, form));
            lecture.setCurrentVersion(version);
            replaceAttachments(version, preparedAttachments);
            replaceCheckpoints(version, form);
            audit.record("lecture_created", "Lecture", lecture.getId(), safeTitle(version));
            return toResponse(lecture);
        } catch (RuntimeException ex) {
            cleanupNewUploads(preparedAttachments);
            throw ex;
        }
    }

    @Transactional
    public LectureResponse updateLecture(Long id, LectureForm form) {
        return updateLecture(id, form, List.of());
    }

    @Transactional
    public LectureResponse updateLecture(Long id, LectureForm form, List<MultipartFile> attachmentFiles) {
        List<PreparedAttachment> preparedAttachments = prepareAttachments(form.getAttachments(), attachmentFiles);
        try {
            validateDraft(form, preparedAttachments);
            Lecture lecture = findLecture(id);
            if (lecture.getStatus() == LectureStatus.ARCHIVED) {
                throw new LectureException("lecture_archived");
            }
            LectureVersion current = requireCurrentVersion(lecture);
            LectureVersion target;
            if (lecture.getStatus() == LectureStatus.PUBLISHED) {
                int nextVersionNo = versions.findMaxVersionNoByLectureId(lecture.getId()) + 1;
                target = versions.save(buildVersion(lecture, nextVersionNo, form));
            } else {
                LectureVersion replacement = buildVersion(lecture, current.getVersionNo(), form);
                current.replaceContent(replacement);
                target = current;
                lecture.changeStatus(LectureStatus.DRAFT);
            }
            replaceAttachments(target, preparedAttachments);
            replaceCheckpoints(target, form);
            audit.record("lecture_updated", "Lecture", lecture.getId(), safeTitle(target));
            return toResponse(lecture);
        } catch (RuntimeException ex) {
            cleanupNewUploads(preparedAttachments);
            throw ex;
        }
    }

    @Transactional
    public LectureResponse publish(Long id) {
        Lecture lecture = findLecture(id);
        if (lecture.getStatus() == LectureStatus.ARCHIVED) {
            throw new LectureException("lecture_archived");
        }
        LectureVersion version = requireCurrentVersion(lecture);
        requirePublishable(version);
        if (version.getControlMode() == LectureControlMode.AUTO
                && checkpoints.countByLectureVersionId(version.getId()) == 0) {
            refreshAutoCheckpoints(version);
        }
        if (version.getControlMode() == LectureControlMode.MANUAL
                && checkpoints.countByLectureVersionId(version.getId()) == 0) {
            throw new LectureException("lecture_manual_checkpoints_required");
        }
        if (version.getControlMode() == LectureControlMode.AUTO
                && checkpoints.countByLectureVersionId(version.getId()) == 0) {
            throw new LectureException("lecture_auto_checkpoints_not_found");
        }
        lecture.changeStatus(LectureStatus.PUBLISHED);
        version.markPublished();
        audit.record("lecture_published", "Lecture", lecture.getId(), safeTitle(version));
        return toResponse(lecture);
    }

    @Transactional
    public LectureResponse archive(Long id) {
        Lecture lecture = findLecture(id);
        if (lecture.getStatus() == LectureStatus.ARCHIVED) {
            throw new LectureException("lecture_already_archived");
        }
        lecture.changeStatus(LectureStatus.ARCHIVED);
        audit.record("lecture_archived", "Lecture", lecture.getId(), safeTitle(requireCurrentVersion(lecture)));
        return toResponse(lecture);
    }

    private Specification<Lecture> filter(Long topicId, LectureStatus status, String query) {
        return (root, criteriaQuery, cb) -> {
            Join<Lecture, LectureVersion> version = root.join("currentVersion", JoinType.LEFT);
            java.util.ArrayList<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (topicId != null) {
                predicates.add(cb.equal(version.get("topic").get("id"), topicId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.toLowerCase(Locale.ROOT).trim() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(version.get("titleRu")), pattern),
                        cb.like(cb.lower(version.get("titleKk")), pattern),
                        cb.like(cb.lower(version.get("source")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private void validateDraft(LectureForm form, List<PreparedAttachment> preparedAttachments) {
        if (form.getTopicId() != null) {
            findTopic(form.getTopicId());
        }
        if (isBlank(form.getTitleRu()) && isBlank(form.getTitleKk())) {
            throw new LectureException("lecture_title_required");
        }
        if (isBlank(form.getContentRu()) && isBlank(form.getContentKk())) {
            throw new LectureException("lecture_content_required");
        }
        if (form.getControlMode() == LectureControlMode.AUTO && requestedAutoCount(form) < 1) {
            throw new LectureException("lecture_auto_checkpoint_count_required");
        }
        validateAttachments(preparedAttachments);
    }

    private void requirePublishable(LectureVersion version) {
        if (version.getTopic() == null) {
            throw new LectureException("lecture_topic_required");
        }
        if (isBlank(version.getTitleRu()) || isBlank(version.getTitleKk())) {
            throw new LectureException("lecture_bilingual_title_required");
        }
        if (isBlankHtml(version.getContentRuHtml()) || isBlankHtml(version.getContentKkHtml())) {
            throw new LectureException("lecture_bilingual_content_required");
        }
    }

    private LectureVersion buildVersion(Lecture lecture, int versionNo, LectureForm form) {
        Topic topic = form.getTopicId() == null ? null : findTopic(form.getTopicId());
        return new LectureVersion(
                lecture,
                versionNo,
                topic,
                trimToNull(form.getTitleRu()),
                trimToNull(form.getTitleKk()),
                sanitizeRichText(form.getContentRu()),
                sanitizeRichText(form.getContentKk()),
                trimToNull(form.getSource()),
                form.getControlMode(),
                form.getControlMode() == LectureControlMode.AUTO ? requestedAutoCount(form) : 0
        );
    }

    private void replaceAttachments(LectureVersion version, List<PreparedAttachment> preparedAttachments) {
        List<LectureAttachment> previous = attachments.findByLectureVersionIdOrderBySortOrderAscIdAsc(version.getId());
        attachments.deleteByLectureVersionId(version.getId());
        attachments.flush();
        int order = 0;
        Set<String> retainedStorageKeys = new LinkedHashSet<>();
        for (PreparedAttachment prepared : preparedAttachments) {
            if (prepared.blank()) {
                continue;
            }
            attachments.save(new LectureAttachment(
                    version,
                    prepared.title(),
                    prepared.url(),
                    prepared.mediaType(),
                    prepared.storageKey(),
                    prepared.originalFileName(),
                    prepared.fileContentType(),
                    prepared.fileSizeBytes(),
                    order++
            ));
            if (!isBlank(prepared.storageKey())) {
                retainedStorageKeys.add(prepared.storageKey());
            }
        }
        for (LectureAttachment old : previous) {
            if (!old.isStoredFile()) {
                continue;
            }
            if (!retainedStorageKeys.contains(old.getStorageKey())) {
                attachmentStorage.deleteIfExists(old.getStorageKey());
            }
        }
    }

    private void replaceCheckpoints(LectureVersion version, LectureForm form) {
        checkpoints.deleteByLectureVersionId(version.getId());
        checkpoints.flush();
        if (version.getControlMode() == LectureControlMode.NONE || version.getTopic() == null) {
            return;
        }
        if (version.getControlMode() == LectureControlMode.AUTO) {
            refreshAutoCheckpoints(version);
            return;
        }
        Set<Long> ids = new LinkedHashSet<>(form.getCheckpointQuestionVersionIds());
        int order = 0;
        for (Long questionVersionId : ids) {
            QuestionVersion questionVersion = questionVersions.findById(questionVersionId)
                    .orElseThrow(() -> new LectureException("question_version_not_found"));
            if (questionVersion.getQuestion().getStatus() != QuestionStatus.PUBLISHED) {
                throw new LectureException("checkpoint_question_not_published");
            }
            if (!Objects.equals(questionVersion.getTopic().getId(), version.getTopic().getId())) {
                throw new LectureException("checkpoint_topic_mismatch");
            }
            checkpoints.save(new LectureCheckpoint(version, questionVersion, order++, LectureControlMode.MANUAL));
        }
    }

    private void refreshAutoCheckpoints(LectureVersion version) {
        if (version.getTopic() == null || version.getControlMode() != LectureControlMode.AUTO) {
            return;
        }
        checkpoints.deleteByLectureVersionId(version.getId());
        checkpoints.flush();
        List<QuestionVersion> selected = questionVersions.findPublishedByTopicId(
                version.getTopic().getId(),
                PageRequest.of(0, Math.max(1, version.getCheckpointCountRequested()))
        );
        int order = 0;
        for (QuestionVersion questionVersion : selected) {
            checkpoints.save(new LectureCheckpoint(version, questionVersion, order++, LectureControlMode.AUTO));
        }
    }

    private List<PreparedAttachment> prepareAttachments(List<LectureAttachmentForm> forms, List<MultipartFile> files) {
        List<LectureAttachmentForm> safeForms = forms == null ? List.of() : forms;
        List<MultipartFile> safeFiles = files == null ? List.of() : files;
        int rows = Math.max(safeForms.size(), safeFiles.size());
        java.util.ArrayList<PreparedAttachment> prepared = new java.util.ArrayList<>(rows);
        for (int index = 0; index < rows; index++) {
            LectureAttachmentForm form = index < safeForms.size() && safeForms.get(index) != null
                    ? safeForms.get(index)
                    : new LectureAttachmentForm();
            MultipartFile file = index < safeFiles.size() ? safeFiles.get(index) : null;
            prepared.add(prepareAttachment(form, file));
        }
        return prepared;
    }

    private PreparedAttachment prepareAttachment(LectureAttachmentForm form, MultipartFile file) {
        MultipartFile nonNullFile = file;
        if (nonNullFile != null && !nonNullFile.isEmpty()) {
            LectureAttachmentStorageService.StoredAttachment stored = attachmentStorage.store(nonNullFile);
            String title = isBlank(form.getTitle())
                    ? trimToNull(stored.originalFileName())
                    : form.getTitle().trim();
            String mediaType = normalizedMediaType(stored.mediaType());
            return new PreparedAttachment(
                    title,
                    stored.url(),
                    mediaType,
                    stored.storageKey(),
                    stored.originalFileName(),
                    stored.contentType(),
                    stored.sizeBytes(),
                    true,
                    false
            );
        }

        String title = trimToNull(form.getTitle());
        String url = trimToNull(form.getUrl());
        String mediaType = normalizedMediaType(form.getMediaType());
        String storageKey = trimToNull(form.getStorageKey());
        if (storageKey == null && !isBlank(url)) {
            storageKey = attachmentStorage.extractStorageKey(url);
        }
        if (storageKey != null) {
            url = attachmentStorage.publicUrl(storageKey);
        }
        String originalFileName = trimToNull(form.getOriginalFileName());
        String fileContentType = trimToNull(form.getFileContentType());
        Long fileSizeBytes = form.getFileSizeBytes();
        boolean blank = isBlank(title) && isBlank(url);
        return new PreparedAttachment(
                title,
                url,
                mediaType,
                storageKey,
                originalFileName,
                fileContentType,
                fileSizeBytes,
                false,
                blank
        );
    }

    private void cleanupNewUploads(List<PreparedAttachment> preparedAttachments) {
        for (PreparedAttachment attachment : preparedAttachments) {
            if (!attachment.newUpload()) {
                continue;
            }
            attachmentStorage.deleteIfExists(attachment.storageKey());
        }
    }

    private void validateAttachments(List<PreparedAttachment> preparedAttachments) {
        int nonBlankAttachments = 0;
        for (PreparedAttachment attachment : preparedAttachments) {
            if (attachment.blank()) {
                continue;
            }
            nonBlankAttachments += 1;
            if (nonBlankAttachments > MAX_ATTACHMENTS) {
                throw new LectureException("lecture_attachment_limit_exceeded");
            }
            if (isBlank(attachment.title()) || isBlank(attachment.url()) || isBlank(attachment.mediaType())) {
                throw new LectureException("lecture_attachment_required");
            }
            String mediaType = normalizedMediaType(attachment.mediaType());
            if (!ALLOWED_ATTACHMENT_TYPES.contains(mediaType)) {
                throw new LectureException("lecture_attachment_media_type_invalid");
            }
            String url = attachment.url().trim();
            if (!isSafeAttachmentUrl(url)) {
                throw new LectureException("lecture_attachment_url_invalid");
            }
            if (!isAttachmentUrlCompatible(mediaType, url)) {
                throw new LectureException("lecture_attachment_type_url_mismatch");
            }
        }
    }

    private LectureResponse toResponse(Lecture lecture) {
        LectureVersion version = lecture.getCurrentVersion();
        List<LectureAttachmentResponse> attachmentResponses = version == null
                ? List.of()
                : attachments.findByLectureVersionIdOrderBySortOrderAscIdAsc(version.getId()).stream()
                        .map(item -> new LectureAttachmentResponse(
                                item.getId(),
                                item.getTitle(),
                                item.getUrl(),
                                item.getMediaType(),
                                item.getStorageKey(),
                                item.getOriginalFileName(),
                                item.getFileContentType(),
                                item.getFileSizeBytes(),
                                item.isStoredFile(),
                                item.getSortOrder()
                        ))
                        .toList();
        List<LectureCheckpointResponse> checkpointResponses = version == null
                ? List.of()
                : checkpoints.findByLectureVersionIdOrderBySortOrderAscIdAsc(version.getId()).stream()
                        .map(item -> new LectureCheckpointResponse(
                                item.getId(),
                                item.getQuestionVersion().getId(),
                                item.getQuestionVersion().getType().name(),
                                item.getQuestionVersion().getBodyRu(),
                                item.getQuestionVersion().getBodyKk(),
                                item.getSortOrder()
                        ))
                        .toList();
        return new LectureResponse(
                lecture.getId(),
                lecture.getStatus().apiValue(),
                version == null ? null : version.getId(),
                version == null ? 0 : version.getVersionNo(),
                version == null || version.getTopic() == null ? null : version.getTopic().getId(),
                version == null || version.getTopic() == null ? null : version.getTopic().getTitleRu(),
                version == null || version.getTopic() == null ? null : version.getTopic().getTitleKk(),
                version == null ? null : version.getTitleRu(),
                version == null ? null : version.getTitleKk(),
                version == null ? null : version.getContentRuHtml(),
                version == null ? null : version.getContentKkHtml(),
                version == null ? null : version.getSource(),
                version == null ? null : version.getControlMode().apiValue(),
                version == null ? 0 : version.getCheckpointCountRequested(),
                attachmentResponses.size(),
                checkpointResponses.size(),
                attachmentResponses,
                checkpointResponses,
                lecture.getUpdatedAt(),
                version == null ? null : version.getPublishedAt()
        );
    }

    private Lecture findLecture(Long id) {
        return lectures.findById(id).orElseThrow(() -> new LectureException("lecture_not_found"));
    }

    private LectureVersion requireCurrentVersion(Lecture lecture) {
        if (lecture.getCurrentVersion() == null) {
            throw new LectureException("lecture_version_not_found");
        }
        return lecture.getCurrentVersion();
    }

    private Topic findTopic(Long id) {
        return topics.findById(id).orElseThrow(() -> new LectureException("topic_not_found"));
    }

    private AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return users.findByEmailIgnoreCase(authentication.getName()).orElse(null);
    }

    private int requestedAutoCount(LectureForm form) {
        return form.getAutoCheckpointCount() <= 0 ? DEFAULT_AUTO_CHECKPOINT_COUNT : form.getAutoCheckpointCount();
    }

    private String sanitizeRichText(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim().replace("\r\n", "\n").replace('\r', '\n');
        Document.OutputSettings output = new Document.OutputSettings();
        output.prettyPrint(false);
        String safe = Jsoup.clean(normalized, SAFE_BASE_URI, LECTURE_SAFE_HTML, output).trim();
        if (safe.isBlank()) {
            return null;
        }
        Document doc = Jsoup.parseBodyFragment(safe, SAFE_BASE_URI);
        normalizeLectureLinks(doc);
        normalizeFormulaSpans(doc);
        String normalizedSafeHtml = doc.body().html().trim();
        return normalizedSafeHtml.isBlank() ? null : normalizedSafeHtml;
    }

    private void normalizeLectureLinks(Document doc) {
        for (Element link : doc.select("a")) {
            String href = trimToNull(link.attr("href"));
            if (href == null || !isSafeLectureHref(href)) {
                link.removeAttr("href");
                link.removeAttr("target");
                link.removeAttr("rel");
                continue;
            }
            link.attr("href", href);
            String target = trimToNull(link.attr("target"));
            if (target == null) {
                link.removeAttr("target");
                link.removeAttr("rel");
                continue;
            }
            String targetLower = target.toLowerCase(Locale.ROOT);
            if ("_blank".equals(targetLower)) {
                link.attr("target", "_blank");
                link.attr("rel", "noopener noreferrer nofollow");
            } else if ("_self".equals(targetLower)) {
                link.attr("target", "_self");
                link.removeAttr("rel");
            } else {
                link.removeAttr("target");
                link.removeAttr("rel");
            }
        }
    }

    private void normalizeFormulaSpans(Document doc) {
        for (Element span : doc.select("span.ql-formula")) {
            String formula = sanitizeFormulaValue(span.attr("data-value"));
            if (formula == null) {
                span.remove();
                continue;
            }
            span.clearAttributes();
            span.attr("class", "ql-formula");
            span.attr("data-value", formula);
            span.attr("contenteditable", "false");
        }
        for (Element span : doc.select("span[data-value]:not(.ql-formula)")) {
            span.removeAttr("data-value");
            span.removeAttr("contenteditable");
        }
    }

    private String sanitizeFormulaValue(String rawFormula) {
        String value = trimToNull(rawFormula);
        if (value == null) {
            return null;
        }
        String singleLine = value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
        String compact = singleLine.replaceAll("\\s{2,}", " ").trim();
        if (compact.isBlank()) {
            return null;
        }
        if (compact.length() > MAX_FORMULA_LENGTH) {
            compact = compact.substring(0, MAX_FORMULA_LENGTH);
        }
        if (UNSAFE_FORMULA_PATTERN.matcher(compact).find()) {
            return null;
        }
        return compact;
    }

    private boolean isSafeLectureHref(String href) {
        String value = href.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("http://")
                || value.startsWith("https://")
                || value.startsWith("mailto:")
                || value.startsWith("#")
                || value.startsWith("/")
                || value.startsWith("./")
                || value.startsWith("../")
                || value.startsWith("?");
    }

    private boolean isSafeAttachmentUrl(String rawUrl) {
        String value = rawUrl.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
            return false;
        }
        if (value.startsWith("/") || value.startsWith("./") || value.startsWith("../")) {
            return true;
        }
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private boolean isAttachmentUrlCompatible(String mediaType, String url) {
        String path = attachmentPath(url);
        return switch (mediaType) {
            case "pdf" -> path.endsWith(".pdf");
            case "image" -> hasAnySuffix(path, IMAGE_EXTENSIONS);
            case "video" -> hasAnySuffix(path, VIDEO_EXTENSIONS)
                    || path.contains("youtube.com/")
                    || path.contains("youtu.be/")
                    || path.contains("vimeo.com/");
            default -> true;
        };
    }

    private String attachmentPath(String url) {
        String lower = url.toLowerCase(Locale.ROOT).trim();
        int query = lower.indexOf('?');
        String withoutQuery = query >= 0 ? lower.substring(0, query) : lower;
        int fragment = withoutQuery.indexOf('#');
        return fragment >= 0 ? withoutQuery.substring(0, fragment) : withoutQuery;
    }

    private boolean hasAnySuffix(String value, Set<String> suffixes) {
        for (String suffix : suffixes) {
            if (value.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private String editableContent(String value) {
        return value;
    }

    private String safeTitle(LectureVersion version) {
        if (!isBlank(version.getTitleRu())) {
            return version.getTitleRu();
        }
        if (!isBlank(version.getTitleKk())) {
            return version.getTitleKk();
        }
        return "lecture";
    }

    private boolean isBlankHtml(String value) {
        if (value == null) {
            return true;
        }
        return Jsoup.parse(value).text().isBlank();
    }

    private String normalizedMediaType(String value) {
        if (isBlank(value)) {
            return "link";
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private record PreparedAttachment(
            String title,
            String url,
            String mediaType,
            String storageKey,
            String originalFileName,
            String fileContentType,
            Long fileSizeBytes,
            boolean newUpload,
            boolean blank
    ) {
    }
}
