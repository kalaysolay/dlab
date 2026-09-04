package kz.damulab.content;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Выполняет административные CRUD-операции с учебными предметами. */
@Service
public class SubjectManagementService {

    private final SubjectRepository subjects;
    private final TopicRepository topics;
    private final SubjectIconStorageService iconStorage;

    /** Подключает предметы, темы и файловое хранилище. */
    public SubjectManagementService(
            SubjectRepository subjects,
            TopicRepository topics,
            SubjectIconStorageService iconStorage
    ) {
        this.subjects = subjects;
        this.topics = topics;
        this.iconStorage = iconStorage;
    }

    /** Возвращает предметы в алфавитном порядке вместе с количеством тем. */
    @Transactional(readOnly = true)
    public List<AdminSubjectView> list() {
        return subjects.findAllByOrderByTitleRuAsc().stream()
                .map(this::toView)
                .toList();
    }

    /** Возвращает один предмет для страницы редактирования. */
    @Transactional(readOnly = true)
    public AdminSubjectView get(Long id) {
        return toView(findSubject(id));
    }

    /** Преобразует предмет в форму, которую можно редактировать. */
    @Transactional(readOnly = true)
    public SubjectForm form(Long id) {
        Subject subject = findSubject(id);
        SubjectForm form = new SubjectForm();
        form.setTitleRu(subject.getTitleRu());
        form.setTitleKk(subject.getTitleKk());
        form.setDescriptionRu(subject.getDescriptionRu());
        form.setDescriptionKk(subject.getDescriptionKk());
        return form;
    }

    /** Создаёт предмет и при наличии сохраняет его PNG-иконку. */
    @Transactional
    public Subject create(SubjectForm form, MultipartFile icon) {
        Subject subject = new Subject(
                "subject-" + UUID.randomUUID(),
                clean(form.getTitleRu()),
                clean(form.getTitleKk()),
                clean(form.getDescriptionRu()),
                clean(form.getDescriptionKk())
        );
        String newIconKey = storeOptional(icon);
        subject.setIconStorageKey(newIconKey);
        try {
            return subjects.saveAndFlush(subject);
        } catch (RuntimeException ex) {
            deleteWithoutFailing(newIconKey);
            throw ex;
        }
    }

    /** Обновляет тексты и при необходимости заменяет либо удаляет иконку. */
    @Transactional
    public Subject update(Long id, SubjectForm form, MultipartFile icon) {
        Subject subject = findSubject(id);
        String oldIconKey = subject.getIconStorageKey();
        String newIconKey = storeOptional(icon);

        subject.update(
                clean(form.getTitleRu()),
                clean(form.getTitleKk()),
                clean(form.getDescriptionRu()),
                clean(form.getDescriptionKk())
        );
        if (newIconKey != null) {
            subject.setIconStorageKey(newIconKey);
        } else if (form.isRemoveIcon()) {
            subject.setIconStorageKey(null);
        }

        try {
            Subject saved = subjects.saveAndFlush(subject);
            if ((newIconKey != null || form.isRemoveIcon()) && oldIconKey != null) {
                deleteWithoutFailing(oldIconKey);
            }
            return saved;
        } catch (RuntimeException ex) {
            deleteWithoutFailing(newIconKey);
            throw ex;
        }
    }

    /** Удаляет предмет только при полном отсутствии связанных тем. */
    @Transactional
    public void delete(Long id) {
        Subject subject = findSubject(id);
        if (topics.existsBySubject_Id(id)) {
            throw new SubjectManagementException("subject_has_topics");
        }
        try {
            subjects.delete(subject);
            subjects.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new SubjectManagementException("subject_in_use");
        }
        deleteWithoutFailing(subject.getIconStorageKey());
    }

    private Subject findSubject(Long id) {
        return subjects.findById(id)
                .orElseThrow(() -> new SubjectManagementException("subject_not_found"));
    }

    private AdminSubjectView toView(Subject subject) {
        long topicCount = topics.countBySubject_Id(subject.getId());
        String iconUrl = subject.getIconStorageKey() == null
                ? null
                : "/files/subject-icons/" + subject.getIconStorageKey();
        return new AdminSubjectView(
                subject.getId(),
                subject.getCode(),
                subject.getTitleRu(),
                subject.getTitleKk(),
                subject.getDescriptionRu(),
                subject.getDescriptionKk(),
                iconUrl,
                topicCount
        );
    }

    private String storeOptional(MultipartFile icon) {
        if (icon == null || icon.isEmpty()) {
            return null;
        }
        return iconStorage.store(icon);
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private void deleteWithoutFailing(String storageKey) {
        try {
            iconStorage.delete(storageKey);
        } catch (SubjectManagementException ignored) {
            // Ошибка уборки файла не должна отменять уже выполненную операцию с предметом.
        }
    }
}
