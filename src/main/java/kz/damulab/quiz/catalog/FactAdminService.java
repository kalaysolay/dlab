package kz.damulab.quiz.catalog;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Редакционный workflow банка утверждений с полноценным boolean-ключом и неизменяемыми публикациями. */
@Service
public class FactAdminService {
    private final FactSetRepository sets;
    private final FactStatementRepository statements;
    private final FactStatementVersionRepository versions;
    private final EditorialWorkflowService workflow;
    private final QuizContentEditorialEventRepository events;

    public FactAdminService(FactSetRepository sets, FactStatementRepository statements,
            FactStatementVersionRepository versions, EditorialWorkflowService workflow,
            QuizContentEditorialEventRepository events) {
        this.sets = sets; this.statements = statements; this.versions = versions;
        this.workflow = workflow; this.events = events;
    }

    /** Возвращает наборы утверждений для административного списка. */
    public List<FactSet> listSets() { return sets.findAllByOrderByIdAsc(); }
    /** Возвращает утверждения вместе с указателями текущих версий. */
    public List<FactStatement> listStatements() { return statements.findAllByOrderByIdAsc(); }
    /** Загружает набор или выдаёт безопасную бизнес-ошибку. */
    public FactSet requireSet(Long id) { return sets.findById(id).orElseThrow(() -> new ContentValidationException("Набор утверждений не найден")); }
    /** Загружает утверждение или выдаёт безопасную бизнес-ошибку. */
    public FactStatement requireStatement(Long id) { return statements.findById(id).orElseThrow(() -> new ContentValidationException("Утверждение не найдено")); }

    /** Создаёт тематический набор; публикация набора выполняется явным редакционным действием. */
    @Transactional
    public FactSet createSet(FactSetForm form, Long actorId) {
        validateSet(form);
        if (sets.existsByCodeIgnoreCase(form.getCode().strip())) throw new ContentValidationException("Код набора уже используется");
        FactSet set = new FactSet(form.getCode().strip(), form.getTitleRu().strip(), form.getTitleKk().strip(), form.getSourcePolicy().strip());
        set.setCreatedBy(actorId); return sets.save(set);
    }

    /** Создаёт первую черновую редакцию утверждения. Значение false сохраняется как ответ, а не как null. */
    @Transactional
    public FactStatement createStatement(FactStatementForm form, Long actorId) {
        FactSet set = requireSet(form.getSetId()); FactStatement statement = new FactStatement(set);
        statement.setCreatedBy(actorId); statements.save(statement);
        FactStatementVersion version = buildVersion(1, statement, form, actorId);
        versions.save(version); statement.setDraftVersion(version); return statements.save(statement);
    }

    /** Создаёт новую редакцию; опубликованная версия и её boolean-ключ остаются без изменений. */
    @Transactional
    public FactStatementVersion saveDraft(Long statementId, FactStatementForm form, Long actorId) {
        FactStatement statement = requireStatement(statementId);
        if (!statement.getSet().getId().equals(form.getSetId())) throw new ContentValidationException("Нельзя перенести утверждение в другой набор");
        FactStatementVersion version = buildVersion(versions.findMaxVersionNo(statementId) + 1, statement, form, actorId);
        versions.save(version); statement.setDraftVersion(version); statements.save(statement); return version;
    }

    /** Отправляет заполненную редакцию утверждения на проверку. */
    @Transactional
    public void submit(Long statementId, Long actorId) {
        FactStatement s = requireStatement(statementId); FactStatementVersion v = requireDraft(s);
        validateForPublication(v); workflow.submit(v, QuizContentType.FACT_STATEMENT, statementId, actorId);
        if (s.getPublishedVersion() == null) s.setStatus(EditorialStatus.NEEDS_REVIEW);
    }

    /** Одобряет редакцию и фиксирует проверяющего. */
    @Transactional
    public void approve(Long statementId, Long actorId) {
        FactStatement s = requireStatement(statementId); FactStatementVersion v = requireDraft(s);
        validateForPublication(v); workflow.approve(v, QuizContentType.FACT_STATEMENT, statementId, actorId);
        if (s.getPublishedVersion() == null) s.setStatus(EditorialStatus.APPROVED);
    }

    /** Публикует редакцию атомарной сменой указателя. */
    @Transactional
    public void publish(Long statementId, Long actorId) {
        FactStatement s = requireStatement(statementId); FactStatementVersion v = requireDraft(s);
        validateForPublication(v); workflow.publish(v, QuizContentType.FACT_STATEMENT, statementId, actorId);
        s.setPublishedVersion(v); s.setDraftVersion(null); s.setStatus(EditorialStatus.PUBLISHED);
    }

    /** Архивирует утверждение, не удаляя ни одной версии. */
    @Transactional
    public void archiveStatement(Long statementId, Long actorId) {
        FactStatement s = requireStatement(statementId);
        workflow.archive(s, QuizContentType.FACT_STATEMENT, statementId,
                s.getPublishedVersion() == null ? null : s.getPublishedVersion().getId(), actorId);
    }

    /** Проводит набор через простой lifecycle; материалы всё равно публикуются независимо и повторно валидируются. */
    @Transactional
    public void transitionSet(Long setId, EditorialStatus target, Long actorId) {
        FactSet set = requireSet(setId); EditorialStatus from = set.getStatus();
        boolean allowed = (from == EditorialStatus.DRAFT && target == EditorialStatus.NEEDS_REVIEW)
                || (from == EditorialStatus.NEEDS_REVIEW && target == EditorialStatus.APPROVED)
                || (from == EditorialStatus.APPROVED && target == EditorialStatus.PUBLISHED);
        if (!allowed) throw new ContentValidationException("Недопустимый редакционный переход набора: " + from + " → " + target);
        set.setStatus(target); events.save(new QuizContentEditorialEvent(QuizContentType.FACT_SET, setId, null, from, target, actorId));
    }

    /** Архивирует тематический набор без автоматического удаления содержимого. */
    @Transactional
    public void archiveSet(Long setId, Long actorId) {
        FactSet set = requireSet(setId); workflow.archive(set, QuizContentType.FACT_SET, setId, null, actorId);
    }

    /** Собирает форму из draft или текущей опубликованной версии. */
    @Transactional(readOnly = true)
    public FactStatementForm form(Long statementId) {
        FactStatement s = requireStatement(statementId); FactStatementVersion v = s.getDraftVersion() != null ? s.getDraftVersion() : s.getPublishedVersion();
        FactStatementForm f = new FactStatementForm(); f.setSetId(s.getSet().getId());
        if (v == null) return f;
        f.setTextRu(v.getTextRu()); f.setTextKk(v.getTextKk()); f.setTruthValue(v.isTruthValue());
        f.setExplanationRu(v.getExplanationRu()); f.setExplanationKk(v.getExplanationKk());
        f.setSourceReference(v.getSourceReference()); f.setUniversalAudience(v.isUniversalAudience()); f.setGradeIds(new ArrayList<>(v.getGradeIds())); return f;
    }

    private FactStatementVersion buildVersion(int number, FactStatement statement, FactStatementForm f, Long actorId) {
        if (f.getTruthValue() == null) throw new ContentValidationException("Выберите правильный ответ: истина или ложь");
        boolean truth = Boolean.TRUE.equals(f.getTruthValue());
        FactStatementVersion v = new FactStatementVersion(number, statement, safe(f.getTextRu()), safe(f.getTextKk()), truth,
                safe(f.getExplanationRu()), safe(f.getExplanationKk()), safe(f.getSourceReference()));
        v.setUniversalAudience(f.isUniversalAudience()); v.setGradeIds(new LinkedHashSet<>(f.getGradeIds())); v.setCreatedBy(actorId); return v;
    }

    private void validateForPublication(FactStatementVersion v) {
        List<String> errors = new ArrayList<>();
        if (safe(v.getTextRu()).isBlank() || safe(v.getTextKk()).isBlank()) errors.add("Заполните текст утверждения на русском и казахском");
        if (safe(v.getExplanationRu()).isBlank() || safe(v.getExplanationKk()).isBlank()) errors.add("Заполните объяснение на русском и казахском");
        if (safe(v.getSourceReference()).isBlank()) errors.add("Укажите проверяемый источник");
        if (v.isUniversalAudience() == !v.getGradeIds().isEmpty()) errors.add("Выберите явную универсальную аудиторию или хотя бы один класс");
        if (!errors.isEmpty()) throw new ContentValidationException(errors);
    }

    private void validateSet(FactSetForm f) {
        if (safe(f.getCode()).isBlank() || !safe(f.getCode()).matches("[A-Za-z0-9_-]{2,96}")) throw new ContentValidationException("Код набора: 2–96 латинских букв, цифр, _ или -");
        if (safe(f.getTitleRu()).isBlank() || safe(f.getTitleKk()).isBlank() || safe(f.getSourcePolicy()).isBlank()) throw new ContentValidationException("Заполните оба названия и политику источников");
    }
    private FactStatementVersion requireDraft(FactStatement s) { if (s.getDraftVersion() == null) throw new ContentValidationException("У утверждения нет черновой редакции"); return s.getDraftVersion(); }
    private static String safe(String v) { return v == null ? "" : v.strip(); }
}
