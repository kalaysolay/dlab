package kz.damulab.quiz.catalog;

import java.time.OffsetDateTime;
import kz.damulab.config.DamulabQuizProperties;
import org.springframework.stereotype.Service;

/** Единые переходы и аудит банков/игр; контроллеры не меняют статусы напрямую. */
@Service
public class EditorialWorkflowService {
    private final QuizContentEditorialEventRepository events;
    private final DamulabQuizProperties properties;

    public EditorialWorkflowService(QuizContentEditorialEventRepository events, DamulabQuizProperties properties) {
        this.events = events; this.properties = properties;
    }

    /** Переводит завершённый черновик на проверку. */
    public void submit(QuizVersionRecord version, QuizContentType type, Long contentId, Long actorId) {
        transition(version, type, contentId, actorId, EditorialStatus.DRAFT, EditorialStatus.NEEDS_REVIEW);
    }

    /** Одобряет редакцию и фиксирует проверяющего; строгая самопроверка включается только настройкой. */
    public void approve(QuizVersionRecord version, QuizContentType type, Long contentId, Long actorId) {
        // damulab.quiz.strict-self-review по умолчанию false; включение запрещает автору утверждать свою редакцию.
        if (properties.isStrictSelfReview() && actorId.equals(version.getCreatedBy())) {
            throw new ContentValidationException("Автор не может одобрить собственную редакцию при включённой строгой проверке");
        }
        transition(version, type, contentId, actorId, EditorialStatus.NEEDS_REVIEW, EditorialStatus.APPROVED);
        version.recordReview(actorId, OffsetDateTime.now());
    }

    /** Помечает одобренную редакцию опубликованной после предметной валидации вызывающего сервиса. */
    public void publish(QuizVersionRecord version, QuizContentType type, Long contentId, Long actorId) {
        transition(version, type, contentId, actorId, EditorialStatus.APPROVED, EditorialStatus.PUBLISHED);
        version.setPublishedAt(OffsetDateTime.now());
    }

    /** Записывает архивирование стабильного объекта, не меняя и не удаляя опубликованную версию. */
    public void archive(QuizEditorialRecord record, QuizContentType type, Long contentId, Long versionId, Long actorId) {
        EditorialStatus from = record.getStatus();
        if (from == EditorialStatus.ARCHIVED) return;
        record.setStatus(EditorialStatus.ARCHIVED);
        events.save(new QuizContentEditorialEvent(type, contentId, versionId, from, EditorialStatus.ARCHIVED, actorId));
    }

    private void transition(QuizVersionRecord version, QuizContentType type, Long contentId, Long actorId,
                            EditorialStatus expected, EditorialStatus target) {
        if (version.getStatus() != expected) {
            throw new ContentValidationException("Недопустимый редакционный переход: " + version.getStatus() + " → " + target);
        }
        version.setStatus(target);
        events.save(new QuizContentEditorialEvent(type, contentId, version.getId(), expected, target, actorId));
    }
}
