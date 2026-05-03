package kz.damulab.content;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.damulab.audit.AdminContentAuditService;

@Service
public class ContentGraphService {

    private final SubjectRepository subjects;
    private final GradeRepository grades;
    private final TopicRepository topics;
    private final AtomicSkillRepository skills;
    private final AdminContentAuditService audit;
    private final List<ContentDependencyChecker> dependencyCheckers;

    public ContentGraphService(
            SubjectRepository subjects,
            GradeRepository grades,
            TopicRepository topics,
            AtomicSkillRepository skills,
            AdminContentAuditService audit,
            List<ContentDependencyChecker> dependencyCheckers
    ) {
        this.subjects = subjects;
        this.grades = grades;
        this.topics = topics;
        this.skills = skills;
        this.audit = audit;
        this.dependencyCheckers = dependencyCheckers;
    }

    @Transactional(readOnly = true)
    public List<ReferenceOption> listSubjects() {
        return subjects.findAllByOrderByTitleRuAsc().stream()
                .map(subject -> new ReferenceOption(
                        subject.getId(),
                        subject.getCode(),
                        subject.getTitleRu(),
                        subject.getTitleKk()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GradeOption> listGrades() {
        return grades.findAllByOrderByGradeNoAsc().stream()
                .map(grade -> new GradeOption(grade.getId(), grade.getGradeNo(), grade.getTitleRu(), grade.getTitleKk()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ContentReferencesResponse references(Long subjectId, Long gradeId) {
        Long resolvedSubjectId = resolveSubjectId(subjectId);
        Long resolvedGradeId = resolveGradeId(gradeId);
        return new ContentReferencesResponse(
                listSubjects(),
                listGrades(),
                listTopics(resolvedSubjectId, resolvedGradeId)
        );
    }

    @Transactional(readOnly = true)
    public List<TopicResponse> listTopics(Long subjectId, Long gradeId) {
        Long resolvedSubjectId = resolveSubjectId(subjectId);
        Long resolvedGradeId = resolveGradeId(gradeId);
        return topics.findBySubjectIdAndGradeIdOrderByTitleRuAsc(resolvedSubjectId, resolvedGradeId).stream()
                .map(this::toTopicResponse)
                .toList();
    }

    /** Все темы предмета (все классы) — для мультивыбора тем у вопроса. */
    @Transactional(readOnly = true)
    public List<TopicResponse> listTopicsForSubject(Long subjectId) {
        Long resolvedSubjectId = resolveSubjectId(subjectId);
        return topics.findBySubject_IdOrderByTitleRuAsc(resolvedSubjectId).stream()
                .map(this::toTopicResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TopicTreeNode> topicTree(Long subjectId, Long gradeId) {
        Long resolvedSubjectId = resolveSubjectId(subjectId);
        Long resolvedGradeId = resolveGradeId(gradeId);
        List<Topic> allTopics = topics.findBySubjectIdAndGradeIdOrderByTitleRuAsc(resolvedSubjectId, resolvedGradeId);
        Map<Long, List<Topic>> byParent = allTopics.stream()
                .collect(LinkedHashMap::new, (map, topic) -> {
                    Long parentId = topic.getParentTopic() == null ? null : topic.getParentTopic().getId();
                    map.computeIfAbsent(parentId, ignored -> new java.util.ArrayList<>()).add(topic);
                }, Map::putAll);
        byParent.values().forEach(items -> items.sort(Comparator.comparing(Topic::getTitleRu)));
        return byParent.getOrDefault(null, List.of()).stream()
                .map(topic -> toTreeNode(topic, byParent))
                .toList();
    }

    @Transactional(readOnly = true)
    public TopicResponse getTopic(Long id) {
        return toTopicResponse(findTopic(id));
    }

    @Transactional
    public TopicResponse createTopic(TopicForm form) {
        Subject subject = findSubject(form.getSubjectId());
        Grade grade = findGrade(form.getGradeId());
        Topic parent = resolveParent(form.getParentId(), subject, grade);
        String code = normalizeCode(form.getCode(), form.getTitleRu());
        requireNoDuplicate(subject.getId(), grade.getId(), parentId(parent), code, form.getTitleRu(), form.getTitleKk(), null);

        Topic saved = topics.save(new Topic(
                subject,
                grade,
                parent,
                code,
                form.getTitleRu().trim(),
                form.getTitleKk().trim()
        ));
        audit.record("topic_created", "Topic", saved.getId(), saved.getCode());
        return toTopicResponse(saved);
    }

    @Transactional
    public TopicResponse updateTopic(Long id, TopicForm form) {
        Topic topic = findTopic(id);
        Subject subject = findSubject(form.getSubjectId());
        Grade grade = findGrade(form.getGradeId());
        Topic parent = resolveParent(form.getParentId(), subject, grade);
        if (parent != null) {
            requireNotSelfOrDescendant(topic, parent);
        }
        String code = normalizeCode(form.getCode(), form.getTitleRu());
        requireNoDuplicate(subject.getId(), grade.getId(), parentId(parent), code, form.getTitleRu(), form.getTitleKk(), id);

        topic.update(subject, grade, parent, code, form.getTitleRu().trim(), form.getTitleKk().trim());
        audit.record("topic_updated", "Topic", topic.getId(), topic.getCode());
        return toTopicResponse(topic);
    }

    @Transactional
    public void deleteTopic(Long id) {
        Topic topic = findTopic(id);
        if (topics.existsByParentTopicId(id)) {
            throw new ContentGraphException("topic_has_children");
        }
        if (skills.existsByTopicId(id)) {
            throw new ContentGraphException("topic_has_skills");
        }
        dependencyCheckers.stream()
                .filter(checker -> checker.hasTopicDependency(id))
                .findFirst()
                .ifPresent(checker -> {
                    throw new ContentGraphException("topic_has_" + checker.dependencyCode());
                });
        topics.delete(topic);
        audit.record("topic_deleted", "Topic", id, topic.getCode());
    }

    @Transactional(readOnly = true)
    public List<AtomicSkillResponse> listSkills(Long topicId) {
        findTopic(topicId);
        return skills.findByTopicIdOrderByTitleRuAsc(topicId).stream()
                .map(skill -> new AtomicSkillResponse(
                        skill.getId(),
                        skill.getTopic().getId(),
                        skill.getCode(),
                        skill.getTitleRu(),
                        skill.getTitleKk(),
                        skill.isActive()
                ))
                .toList();
    }

    @Transactional
    public AtomicSkillResponse createSkill(Long topicId, AtomicSkillForm form) {
        Topic topic = findTopic(topicId);
        requireMatchingTopic(topicId, form.getTopicId());
        String code = normalizeCode(form.getCode(), form.getTitleRu());
        requireNoSkillDuplicate(topic.getId(), code, form.getTitleRu(), form.getTitleKk(), null);

        AtomicSkill saved = skills.save(new AtomicSkill(
                topic,
                code,
                form.getTitleRu().trim(),
                form.getTitleKk().trim(),
                form.isActive()
        ));
        audit.record("skill_created", "AtomicSkill", saved.getId(), saved.getCode());
        return toSkillResponse(saved);
    }

    @Transactional
    public AtomicSkillResponse updateSkill(Long skillId, AtomicSkillForm form) {
        AtomicSkill skill = findSkill(skillId);
        Topic topic = findTopic(form.getTopicId());
        String code = normalizeCode(form.getCode(), form.getTitleRu());
        requireNoSkillDuplicate(topic.getId(), code, form.getTitleRu(), form.getTitleKk(), skillId);

        skill.update(topic, code, form.getTitleRu().trim(), form.getTitleKk().trim(), form.isActive());
        audit.record("skill_updated", "AtomicSkill", skill.getId(), skill.getCode());
        return toSkillResponse(skill);
    }

    @Transactional
    public void deleteSkill(Long skillId) {
        AtomicSkill skill = findSkill(skillId);
        dependencyCheckers.stream()
                .filter(checker -> checker.hasSkillDependency(skillId))
                .findFirst()
                .ifPresent(checker -> {
                    throw new ContentGraphException("skill_has_" + checker.dependencyCode());
                });
        Long id = skill.getId();
        String code = skill.getCode();
        skills.delete(skill);
        audit.record("skill_deleted", "AtomicSkill", id, code);
    }

    private TopicTreeNode toTreeNode(Topic topic, Map<Long, List<Topic>> byParent) {
        return new TopicTreeNode(
                topic.getId(),
                topic.getCode(),
                topic.getTitleRu(),
                topic.getTitleKk(),
                byParent.getOrDefault(topic.getId(), List.of()).stream()
                        .map(child -> toTreeNode(child, byParent))
                        .toList()
        );
    }

    private TopicResponse toTopicResponse(Topic topic) {
        Topic parent = topic.getParentTopic();
        return new TopicResponse(
                topic.getId(),
                topic.getSubject().getId(),
                topic.getSubject().getTitleRu(),
                topic.getGrade().getId(),
                topic.getGrade().getGradeNo(),
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getTitleRu(),
                topic.getCode(),
                topic.getTitleRu(),
                topic.getTitleKk(),
                topic.getCreatedAt(),
                topic.getUpdatedAt(),
                topics.countByParentTopicId(topic.getId()),
                skills.countByTopicId(topic.getId())
        );
    }

    private AtomicSkillResponse toSkillResponse(AtomicSkill skill) {
        return new AtomicSkillResponse(
                skill.getId(),
                skill.getTopic().getId(),
                skill.getCode(),
                skill.getTitleRu(),
                skill.getTitleKk(),
                skill.isActive()
        );
    }

    private Long resolveSubjectId(Long subjectId) {
        if (subjectId != null) {
            return subjectId;
        }
        return subjects.findAllByOrderByTitleRuAsc().stream()
                .findFirst()
                .map(Subject::getId)
                .orElseThrow(() -> new ContentGraphException("subject_not_found"));
    }

    private Long resolveGradeId(Long gradeId) {
        if (gradeId != null) {
            return gradeId;
        }
        return grades.findAllByOrderByGradeNoAsc().stream()
                .findFirst()
                .map(Grade::getId)
                .orElseThrow(() -> new ContentGraphException("grade_not_found"));
    }

    private Subject findSubject(Long id) {
        return subjects.findById(id)
                .orElseThrow(() -> new ContentGraphException("subject_not_found"));
    }

    private Grade findGrade(Long id) {
        return grades.findById(id)
                .orElseThrow(() -> new ContentGraphException("grade_not_found"));
    }

    private Topic findTopic(Long id) {
        return topics.findById(id)
                .orElseThrow(() -> new ContentGraphException("topic_not_found"));
    }

    private AtomicSkill findSkill(Long id) {
        return skills.findById(id)
                .orElseThrow(() -> new ContentGraphException("skill_not_found"));
    }

    private Topic resolveParent(Long parentId, Subject subject, Grade grade) {
        if (parentId == null) {
            return null;
        }
        Topic parent = findTopic(parentId);
        if (!Objects.equals(parent.getSubject().getId(), subject.getId())
                || !Objects.equals(parent.getGrade().getId(), grade.getId())) {
            throw new ContentGraphException("topic_parent_scope_mismatch");
        }
        return parent;
    }

    private void requireNotSelfOrDescendant(Topic topic, Topic newParent) {
        Topic current = newParent;
        while (current != null) {
            if (Objects.equals(current.getId(), topic.getId())) {
                throw new ContentGraphException("topic_parent_cycle");
            }
            current = current.getParentTopic();
        }
    }

    private void requireNoDuplicate(
            Long subjectId,
            Long gradeId,
            Long parentId,
            String code,
            String titleRu,
            String titleKk,
            Long excludeId
    ) {
        if (topics.existsDuplicate(
                subjectId,
                gradeId,
                parentId,
                code.trim(),
                titleRu.trim(),
                titleKk.trim(),
                excludeId
        )) {
            throw new ContentGraphException("topic_duplicate");
        }
    }

    private void requireNoSkillDuplicate(Long topicId, String code, String titleRu, String titleKk, Long excludeId) {
        if (skills.existsDuplicate(topicId, code.trim(), titleRu.trim(), titleKk.trim(), excludeId)) {
            throw new ContentGraphException("skill_duplicate");
        }
    }

    private void requireMatchingTopic(Long pathTopicId, Long formTopicId) {
        if (formTopicId == null || !Objects.equals(pathTopicId, formTopicId)) {
            throw new ContentGraphException("skill_topic_mismatch");
        }
    }

    private String normalizeCode(String rawCode, String titleRu) {
        if (rawCode != null && !rawCode.isBlank()) {
            return slugify(rawCode);
        }
        return slugify(titleRu);
    }

    private String slugify(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-")
                .replaceAll("(^-|-$)", "");
        if (normalized.isBlank()) {
            throw new ContentGraphException("topic_code_required");
        }
        return normalized;
    }

    private Long parentId(Topic parent) {
        return parent == null ? null : parent.getId();
    }
}
